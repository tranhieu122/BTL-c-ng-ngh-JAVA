package com.hieu.edurepo.config;

import com.hieu.edurepo.util.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Bộ kiểm định cấu hình môi trường Sản xuất (Production Configuration Validator).
 * <p>
 * Ngăn chặn ứng dụng khởi động trên profile 'prod' nếu thiếu các biến môi trường nhạy cảm bắt buộc:
 * DB_URL, DB_USERNAME, DB_PASSWORD, UPLOAD_DIR, MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD,
 * hoặc mật khẩu quản trị viên mặc định không đạt tiêu chuẩn an toàn bảo mật.
 * </p>
 */
@Configuration
@Profile("prod")
public class ProductionConfigurationValidator {

    public ProductionConfigurationValidator(
            @Value("${spring.datasource.url:}") String databaseUrl,
            @Value("${spring.datasource.username:}") String databaseUsername,
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${app.upload.dir:}") String uploadDirectory,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean mailAuthentication,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {

        // Kiểm tra bắt buộc phải có kết nối cơ sở dữ liệu sản xuất
        require(databaseUrl, "Production requires DB_URL (Môi trường Production bắt buộc phải cấu hình biến DB_URL)");
        require(databaseUsername, "Production requires DB_USERNAME (Môi trường Production bắt buộc phải cấu hình biến DB_USERNAME)");
        require(databasePassword, "Production requires DB_PASSWORD (Môi trường Production bắt buộc phải cấu hình biến DB_PASSWORD)");

        // Kiểm tra thư mục lưu trữ học liệu
        require(uploadDirectory, "Production requires UPLOAD_DIR (Môi trường Production bắt buộc phải cấu hình biến UPLOAD_DIR)");

        // Kiểm tra cấu hình dịch vụ thư điện tử
        require(mailHost, "Production requires MAIL_HOST (Môi trường Production bắt buộc phải cấu hình biến MAIL_HOST)");
        if (mailAuthentication) {
            require(mailUsername, "Production SMTP authentication requires MAIL_USERNAME");
            require(mailPassword, "Production SMTP authentication requires MAIL_PASSWORD");
        }

        // Kiểm tra cặp thông tin tài khoản Quản trị viên khởi tạo
        boolean hasAdminEmail = hasText(adminEmail);
        boolean hasAdminPassword = hasText(adminPassword);
        if (hasAdminEmail != hasAdminPassword) {
            throw new IllegalStateException(
                    "APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD must either both be set or both be absent (Biến APP_ADMIN_EMAIL và APP_ADMIN_PASSWORD bắt buộc phải cùng xuất hiện hoặc cùng vắng mặt).");
        }
        if (hasAdminPassword && !PasswordPolicy.isValid(adminPassword)) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD does not meet the password policy (Mật khẩu APP_ADMIN_PASSWORD không đạt chính sách độ mạnh bảo mật).");
        }
    }

    private static void require(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
