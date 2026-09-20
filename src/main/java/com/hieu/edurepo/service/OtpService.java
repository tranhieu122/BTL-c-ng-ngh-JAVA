package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.AuthOtpToken;
import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.observability.OperationalMetrics;
import com.hieu.edurepo.repository.AuthOtpTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

/**
 * Service quản lý vòng đời mã OTP (One-Time Password) dùng trong đăng ký và đặt lại mật khẩu.
 *
 * <h3>Bảo mật:</h3>
 * <ul>
 *   <li>Mã 6 chữ số được tạo bằng {@code SecureRandom} (khó đoán hơn {@code Random}).</li>
 *   <li>Chỉ lưu hash(pepper + purpose + email + mã) vào CSDL – không lưu mã gốc.</li>
 *   <li>So sánh hash bằng {@code MessageDigest.isEqual()} (constant-time) để chống timing attack.</li>
 *   <li>Pessimistic write lock khi verify để tránh race condition (TOCTOU attack).</li>
 *   <li>Cooldown giữa các lần gửi lại để chống spam email.</li>
 *   <li>Giới hạn số lần nhập sai (lockout) để chống brute-force.</li>
 * </ul>
 *
 * <h3>Cấu hình ({@code application.properties}):</h3>
 * <pre>
 * app.auth.otp.pepper=&lt;chuỗi bí mật&gt;     # Pepper trộn vào hash
 * app.auth.otp.ttl-minutes=10              # Thời gian hiệu lực OTP (phút)
 * app.auth.otp.resend-cooldown-seconds=60  # Thời gian chờ giữa các lần gửi lại
 * app.auth.otp.max-attempts=5              # Số lần nhập sai tối đa
 * </pre>
 */
@Service
@Transactional
public class OtpService {
    // SecureRandom dùng để tạo mã OTP khó đoán hơn Random thông thường.
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthOtpTokenRepository tokens;
    private final Clock clock;
    // Pepper là chuỗi bí mật trộn thêm vào trước khi hash OTP.
    // Nhờ vậy, nếu database bị lộ thì kẻ xấu vẫn khó dò ngược mã OTP gốc.
    private final String pepper;
    private final int ttlMinutes;
    private final int resendCooldownSeconds;
    private final int maxAttempts;
    private final OperationalMetrics metrics;

    public OtpService(AuthOtpTokenRepository tokens,
                      Clock clock,
                      @Value("${app.auth.otp.pepper:dev-only-not-for-production}") String pepper,
                      @Value("${app.auth.otp.ttl-minutes:10}") int ttlMinutes,
                      @Value("${app.auth.otp.resend-cooldown-seconds:60}") int resendCooldownSeconds,
                      @Value("${app.auth.otp.max-attempts:5}") int maxAttempts,
                      OperationalMetrics metrics) {
        this.tokens = tokens;
        this.clock = clock;
        this.pepper = pepper;
        this.ttlMinutes = ttlMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
        this.metrics = metrics;
    }

    public OtpIssue issue(String email, OtpPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        LocalDateTime now = now();

        // Tìm OTP mới nhất còn hiệu lực với Row Lock để kiểm tra người dùng có bấm gửi lại quá nhanh không.
        AuthOtpToken existing = tokens.findFirstByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedEmail, purpose)
                .orElse(null);
        if (existing != null && existing.getResendAvailableAt().isAfter(now) && existing.getExpiresAt().isAfter(now)) {
            throw new OtpCooldownException(Math.max(1, java.time.Duration.between(now, existing.getResendAvailableAt()).toSeconds()));
        }

        // Khi tạo OTP mới, các OTP cũ chưa dùng sẽ bị đánh dấu đã dùng để chỉ còn một mã hợp lệ.
        tokens.findByEmailIgnoreCaseAndPurposeAndConsumedAtIsNull(normalizedEmail, purpose).forEach(token -> {
            token.setConsumedAt(now);
            token.setUpdatedAt(now);
        });

        // Tạo mã 6 chữ số, ví dụ 004821. Mã gốc chỉ dùng để gửi email, không lưu trực tiếp vào database.
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        AuthOtpToken token = new AuthOtpToken();
        token.setEmail(normalizedEmail);
        token.setPurpose(purpose);
        token.setCodeHash(hash(normalizedEmail, purpose, code));
        token.setAttempts(0);
        token.setExpiresAt(now.plusMinutes(ttlMinutes));
        token.setResendAvailableAt(now.plusSeconds(resendCooldownSeconds));
        token.setCreatedAt(now);
        token.setUpdatedAt(now);
        tokens.saveAndFlush(token);
        metrics.otpIssued(purpose);

        // Trả mã gốc cho tầng controller/service gửi mail; database chỉ giữ codeHash.
        return new OtpIssue(token.getId(), code, token.getExpiresAt(), token.getResendAvailableAt());
    }

    /** Revoke only the OTP created by the failed delivery attempt. */
    public void revoke(OtpIssue issue) {
        if (issue == null || issue.tokenId() == null) return;
        LocalDateTime revokedAt = now();
        tokens.findById(issue.tokenId()).filter(token -> token.getConsumedAt() == null).ifPresent(token -> {
            token.setConsumedAt(revokedAt);
            token.setUpdatedAt(revokedAt);
            tokens.saveAndFlush(token);
        });
    }

    public OtpVerification verify(String email, OtpPurpose purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        LocalDateTime now = now();

        // Khóa bi quan (PESSIMISTIC_WRITE) để ngăn chặn tấn công đồng thời (Race Condition TOCTOU & Lost Update).
        AuthOtpToken token = tokens.findFirstByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedEmail, purpose)
                .orElse(null);
        if (token == null || token.getConsumedAt() != null) return OtpVerification.MISSING;

        // Hết hạn thì khóa mã hiện tại, người dùng phải gửi lại mã mới.
        if (!token.getExpiresAt().isAfter(now)) {
            token.setConsumedAt(now);
            token.setUpdatedAt(now);
            tokens.saveAndFlush(token);
            return OtpVerification.EXPIRED;
        }

        // Nếu nhập sai quá số lần cho phép, mã bị khóa để tránh dò OTP.
        if (token.getAttempts() >= maxAttempts) {
            token.setConsumedAt(now);
            token.setUpdatedAt(now);
            tokens.saveAndFlush(token);
            return OtpVerification.LOCKED;
        }

        // Hash mã người dùng nhập rồi so sánh với hash trong database.
        // MessageDigest.isEqual giúp so sánh ổn định hơn so với so sánh chuỗi thông thường.
        if (MessageDigest.isEqual(token.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(normalizedEmail, purpose, code).getBytes(StandardCharsets.UTF_8))) {
            token.setConsumedAt(now);
            token.setUpdatedAt(now);
            tokens.saveAndFlush(token);
            return OtpVerification.VALID;
        }

        // Sai mã thì tăng số lần thử. Nếu chạm giới hạn, khóa luôn mã này.
        token.setAttempts(token.getAttempts() + 1);
        token.setUpdatedAt(now);
        if (token.getAttempts() >= maxAttempts) {
            token.setConsumedAt(now);
            tokens.saveAndFlush(token);
            return OtpVerification.LOCKED;
        }
        tokens.saveAndFlush(token);
        return OtpVerification.INVALID;
    }

    private String hash(String email, OtpPurpose purpose, String code) {
        try {
            // Ghép pepper + mục đích + email + mã OTP để cùng một mã OTP ở email khác vẫn ra hash khác.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((pepper + ":" + purpose.name() + ":" + email + ":" + code)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Không thể hash OTP", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String normalizeEmail(String email) {
        // Chuẩn hóa email trước khi lưu/so sánh để tránh A@gmail.com và a@gmail.com bị xem là khác nhau.
        if (email == null) throw new IllegalArgumentException("Email không được để trống");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 255) throw new IllegalArgumentException("Email không hợp lệ");
        return normalized;
    }

    // Kết quả sau khi tạo OTP: mã gốc để gửi mail, thời hạn mã và thời điểm được phép gửi lại.
    public record OtpIssue(Long tokenId, String code, LocalDateTime expiresAt,
                           LocalDateTime resendAvailableAt) { }

    public enum OtpVerification {
        VALID,
        INVALID,
        EXPIRED,
        LOCKED,
        MISSING
    }

    // Lỗi riêng khi người dùng yêu cầu gửi lại OTP trước thời gian cho phép.
    public static class OtpCooldownException extends RuntimeException {
        private final long secondsRemaining;

        public OtpCooldownException(long secondsRemaining) {
            super("OTP_COOLDOWN");
            this.secondsRemaining = secondsRemaining;
        }

        public long getSecondsRemaining() { return secondsRemaining; }
    }
}
