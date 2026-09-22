package com.hieu.edurepo.service;

import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * Dịch vụ truyền phát thư điện tử (Email Service) phục vụ gửi mã xác thực OTP.
 * <p>
 * Hỗ trợ cơ chế che giấu email riêng tư (masking), đo lường thời gian xử lý và phân loại lỗi gửi thư (Timeout, Cấu hình).
 * </p>
 */
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Trạng thái gửi mã OTP qua email.
     */
    public enum OtpDeliveryStatus {
        /** Gửi thư thành công */
        SUCCESS,
        /** Lỗi thiếu cấu hình SMTP */
        CONFIGURATION_ERROR,
        /** Gửi thư thất bại do mạng hoặc nhà cung cấp mail từ chối */
        DELIVERY_FAILED
    }

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String username;
    private final String password;
    private final boolean authenticationRequired;
    private final OperationalMetrics metrics;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${spring.mail.password:}") String password,
                        @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean authenticationRequired,
                        OperationalMetrics metrics) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
        this.authenticationRequired = authenticationRequired;
        this.metrics = metrics;
    }

    /**
     * Gửi mã OTP tới email người nhận.
     *
     * @param email Địa chỉ email
     * @param code Mã OTP 6 số
     * @param purpose Mục đích sử dụng OTP
     * @return true nếu gửi thành công
     */
    public boolean sendOtp(String email, String code, OtpPurpose purpose) {
        return sendOtpStatus(email, code, purpose) == OtpDeliveryStatus.SUCCESS;
    }

    /**
     * Gửi mã OTP và trả về mã trạng thái chi tiết (phục vụ hiển thị cảnh báo và kiểm toán).
     */
    public OtpDeliveryStatus sendOtpStatus(String email, String code, OtpPurpose purpose) {
        long started = System.nanoTime();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || (authenticationRequired
                && (username == null || username.isBlank() || password == null || password.isBlank()))) {
            metrics.otpEmail(purpose, "SKIPPED", OperationalMetrics.MailFailureType.CONFIGURATION,
                    elapsed(started));
            log.warn("Cấu hình SMTP chưa được thiết lập; bỏ qua việc gửi mã OTP purpose={}", purpose);
            return OtpDeliveryStatus.CONFIGURATION_ERROR;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (username != null && !username.isBlank()) message.setFrom(username);
        message.setTo(email);
        message.setSubject("Mã OTP xác thực EduRepo");
        message.setText("Mã xác thực OTP " + purposeLabel(purpose) + " của bạn là: " + code
                + "\nMã có hiệu lực trong 5 phút. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.");

        try {
            sender.send(message);
            metrics.otpEmail(purpose, "SUCCESS", null, elapsed(started));
            return OtpDeliveryStatus.SUCCESS;
        } catch (MailException exception) {
            OperationalMetrics.MailFailureType failureType = isTimeout(exception)
                    ? OperationalMetrics.MailFailureType.TIMEOUT
                    : OperationalMetrics.MailFailureType.MAIL_PROVIDER;
            metrics.otpEmail(purpose, "FAILED", failureType, elapsed(started));
            log.warn("Gửi mã OTP thất bại purpose={} recipient={} failureType={} exception={}",
                    purpose, maskEmail(email), failureType, exception.getClass().getSimpleName());
            log.debug("Chi tiết ngoại lệ SMTP khi gửi mã OTP", exception);
            return OtpDeliveryStatus.DELIVERY_FAILED;
        }
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String purposeLabel(OtpPurpose purpose) {
        return purpose == OtpPurpose.REGISTER ? "đăng ký tài khoản" : "đặt lại mật khẩu";
    }

    /**
     * Che giấu một phần địa chỉ email khi ghi log an toàn (ví dụ s***@gmail.com).
     */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "unknown";
        int separator = email.indexOf('@');
        if (separator <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(separator);
    }
}
