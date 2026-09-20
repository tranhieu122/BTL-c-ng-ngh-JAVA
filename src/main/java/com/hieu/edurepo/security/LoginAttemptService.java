package com.hieu.edurepo.security;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Service quản lý và ghi nhận số lần đăng nhập thất bại, thực hiện khóa tài khoản tạm thời.
 *
 * <p>Cơ chế anti-brute-force:</p>
 * <ol>
 *   <li>Mỗi lần đăng nhập thất bại: tăng {@code failedLoginAttempts} của tài khoản.</li>
 *   <li>Khi đạt ngưỡng ({@code max-failures}): đặt {@code loginLockedUntil} = hiện tại + {@code lock-minutes}.</li>
 *   <li>Khi đăng nhập thành công: reset về 0 và xóa {@code loginLockedUntil}.</li>
 * </ol>
 *
 * <p>Cấu hình qua {@code application.properties}:</p>
 * <pre>
 * app.security.login.max-failures=5    # Số lần thất bại tối đa trước khi khóa (mặc định: 5)
 * app.security.login.lock-minutes=15   # Thời gian khóa tính bằng phút (mặc định: 15)
 * </pre>
 *
 * <p>Sử dụng pessimistic locking ({@code SELECT ... FOR UPDATE}) qua
 * {@code lockByEmailIgnoreCase} và {@code lockById} để tránh race condition
 * khi nhiều request đăng nhập đồng thời.</p>
 */
@Service
public class LoginAttemptService {

    /** Repository thao tác với người dùng trong CSDL. */
    private final UserRepository users;

    /** Đồng hồ hệ thống (UTC), được inject để dễ mock trong test. */
    private final Clock clock;

    /** Số lần đăng nhập thất bại tối đa trước khi khóa tài khoản. */
    private final int maxFailures;

    /** Thời gian khóa tài khoản tính bằng phút. */
    private final long lockMinutes;

    /**
     * Constructor với cấu hình từ application.properties.
     *
     * @param users       Repository người dùng.
     * @param clock       Đồng hồ hệ thống.
     * @param maxFailures Số lần thất bại tối đa (mặc định: 5).
     * @param lockMinutes Thời gian khóa (phút, mặc định: 15).
     */
    public LoginAttemptService(UserRepository users,
            Clock clock,
            @Value("${app.security.login.max-failures:5}") int maxFailures,
            @Value("${app.security.login.lock-minutes:15}") long lockMinutes) {
        this.users = users;
        this.clock = clock;
        this.maxFailures = Math.max(1, maxFailures);
        this.lockMinutes = Math.max(1, lockMinutes);
    }

    /**
     * Ghi nhận một lần đăng nhập thất bại.
     * Tăng bộ đếm thất bại và khóa tài khoản nếu vượt ngưỡng.
     * Dùng pessimistic lock để tránh race condition khi nhiều request đồng thời.
     *
     * @param email Email người dùng đã nhập (case-insensitive, trim khoảng trắng).
     */
    @Transactional
    public void recordFailure(String email) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isBlank()) {
            return;
        }
        // lockByEmailIgnoreCase dùng SELECT FOR UPDATE để tránh concurrent update conflict
        users.lockByEmailIgnoreCase(normalizedEmail).ifPresent(user -> applyFailure(user, LocalDateTime.now(clock)));
    }

    /**
     * Ghi nhận đăng nhập thành công: reset bộ đếm thất bại và xóa thời gian khóa.
     * Chỉ cập nhật CSDL nếu thực sự có dữ liệu cần reset (tránh dirty write không cần thiết).
     *
     * @param userId ID người dùng đã đăng nhập thành công.
     */
    @Transactional
    public void recordSuccess(Long userId) {
        if (userId == null) {
            return;
        }
        users.lockById(userId).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLoginLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLoginLockedUntil(null);
            }
        });
    }

    /**
     * Áp dụng logic thất bại vào entity người dùng.
     * Bỏ qua nếu tài khoản đang trong thời gian bị khóa (tránh extend thêm).
     *
     * @param user Entity người dùng (đã được lock).
     * @param now  Thời điểm hiện tại.
     */
    private void applyFailure(User user, LocalDateTime now) {
        // Nếu tài khoản đang bị khóa → không tăng counter thêm
        if (user.isLoginLockedAt(now)) {
            return;
        }
        // Reset counter nếu lock period đã hết trước đó
        int currentFailures = user.getLoginLockedUntil() == null ? user.getFailedLoginAttempts() : 0;
        int nextFailures = Math.min(maxFailures, currentFailures + 1);
        user.setFailedLoginAttempts(nextFailures);
        // Chỉ khóa khi đạt ngưỡng
        user.setLoginLockedUntil(nextFailures >= maxFailures ? now.plusMinutes(lockMinutes) : null);
    }

    /**
     * Chuẩn hóa email: xóa khoảng trắng và chuyển về chữ thường.
     *
     * @param email Email đầu vào.
     * @return Email đã chuẩn hóa, hoặc chuỗi rỗng nếu null.
     */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
