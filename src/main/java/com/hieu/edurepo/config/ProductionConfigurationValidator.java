package com.hieu.edurepo.config;

import com.hieu.edurepo.util.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Fails startup before traffic is accepted when required production configuration is absent. */
@Configuration(proxyBeanMethods = false)
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
        require(databaseUrl, "Production requires DB_URL");
        require(databaseUsername, "Production requires DB_USERNAME");
        require(databasePassword, "Production requires DB_PASSWORD");
        require(uploadDirectory, "Production requires UPLOAD_DIR");
        require(mailHost, "Production requires MAIL_HOST");
        if (mailAuthentication) {
            require(mailUsername, "Production SMTP authentication requires MAIL_USERNAME");
            require(mailPassword, "Production SMTP authentication requires MAIL_PASSWORD");
        }
        boolean hasAdminEmail = hasText(adminEmail);
        boolean hasAdminPassword = hasText(adminPassword);
        if (hasAdminEmail != hasAdminPassword) {
            throw new IllegalStateException(
                    "APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD must either both be set or both be absent");
        }
        if (hasAdminPassword && !PasswordPolicy.isValid(adminPassword)) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD does not meet the password policy");
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
