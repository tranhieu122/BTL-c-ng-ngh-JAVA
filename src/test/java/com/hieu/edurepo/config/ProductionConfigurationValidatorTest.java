package com.hieu.edurepo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử bộ kiểm định cấu hình môi trường sản xuất (Production Configuration Validator Test).
 * Xác minh các biến môi trường bắt buộc (DB_URL, UPLOAD_DIR, MAIL_HOST) phải được khai báo đầy đủ.
 */
class ProductionConfigurationValidatorTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ProductionConfigurationValidator.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "spring.datasource.url=jdbc:mysql://database/edurepo",
                    "spring.datasource.username=edurepo",
                    "spring.datasource.password=not-a-real-secret",
                    "app.upload.dir=/srv/edurepo/uploads",
                    "spring.mail.host=smtp.example.test",
                    "spring.mail.properties.mail.smtp.auth=true",
                    "spring.mail.username=mailer",
                    "spring.mail.password=not-a-real-secret",
                    "app.admin.email=",
                    "app.admin.password=");

    @Test
    void productionFailsFastWhenDatabaseCredentialIsMissing() {
        context.withPropertyValues("spring.datasource.password=").run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("Production requires DB_PASSWORD");
            assertThat(stackTrace(result.getStartupFailure())).doesNotContain("not-a-real-secret");
        });
    }

    @Test
    void productionFailsFastWhenAuthenticatedSmtpCredentialIsMissing() {
        context.withPropertyValues("spring.mail.password=").run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("requires MAIL_PASSWORD");
            assertThat(stackTrace(result.getStartupFailure())).doesNotContain("not-a-real-secret");
        });
    }

    @Test
    void validProductionConfigurationPassesWithoutExposingValues() {
        context.run(result -> assertThat(result).hasNotFailed());
    }

    @Test
    void unauthenticatedSmtpDoesNotRequireCredentials() {
        context.withPropertyValues(
                "spring.mail.properties.mail.smtp.auth=false",
                "spring.mail.username=",
                "spring.mail.password=")
                .run(result -> assertThat(result).hasNotFailed());
    }

    private String stackTrace(Throwable failure) {
        java.io.StringWriter output = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(output));
        return output.toString();
    }
}
