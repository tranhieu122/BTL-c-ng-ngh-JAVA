package com.hieu.edurepo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OtpProductionConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(OtpProductionConfiguration.class)
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void productionFailsFastWhenPepperIsMissing() {
        context.withPropertyValues("app.auth.otp.pepper=").run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("Production requires OTP_HASH_PEPPER");
        });
    }

    @Test
    void productionAcceptsStrongPepper() {
        context.withPropertyValues("app.auth.otp.pepper=0123456789abcdef0123456789abcdef")
                .run(result -> assertThat(result).hasNotFailed());
    }

    @Test
    void productionRejectsShortPepper() {
        context.withPropertyValues("app.auth.otp.pepper=too-short")
                .run(result -> assertThat(result).hasFailed()
                        .getFailure().hasStackTraceContaining("at least 32 characters"));
    }
}
