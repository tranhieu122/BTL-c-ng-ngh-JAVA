package com.hieu.edurepo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Cấu hình kiểm tra tính an toàn của dịch vụ gửi mã OTP trên môi trường Production.
 * <p>
 * Đưa ra cảnh báo nghiêm trọng nếu môi trường sản xuất chưa cấu hình máy chủ SMTP thực tế
 * (tránh trường hợp người dùng đăng ký tài khoản nhưng không thể nhận mã kích hoạt).
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class OtpProductionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OtpProductionConfiguration.class);
    private static final int MINIMUM_PEPPER_LENGTH = 32;

    public OtpProductionConfiguration(
            @Value("${app.auth.otp.pepper:}") String pepper,
            @Value("${spring.mail.host:}") String mailHost) {
        if (pepper == null || pepper.isBlank() || pepper.length() < MINIMUM_PEPPER_LENGTH
                || "dev-only-not-for-production".equals(pepper)) {
            throw new IllegalStateException(
                    "Production requires OTP_HASH_PEPPER with at least 32 characters (Khóa bí mật OTP Pepper phải có ít nhất 32 ký tự trên Production)");
        }

        if (mailHost == null || mailHost.isBlank()) {
            log.warn("CẢNH BÁO BẢO MẬT: Môi trường Production chưa được cấu hình spring.mail.host. Mã xác thực OTP sẽ không thể gửi qua email thực tế!");
        } else {
            log.info("Hệ thống gửi mã xác thực OTP qua SMTP đã sẵn sàng trên Production với máy chủ: {}", mailHost);
        }
    }
}
