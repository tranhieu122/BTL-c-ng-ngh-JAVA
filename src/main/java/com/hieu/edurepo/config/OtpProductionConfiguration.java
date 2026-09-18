package com.hieu.edurepo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Production-only guard against predictable or accidentally missing OTP hashes. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class OtpProductionConfiguration {
    private static final int MINIMUM_PEPPER_LENGTH = 32;

    public OtpProductionConfiguration(@Value("${app.auth.otp.pepper:}") String pepper) {
        if (pepper == null || pepper.isBlank() || pepper.length() < MINIMUM_PEPPER_LENGTH
                || "dev-only-not-for-production".equals(pepper)) {
            throw new IllegalStateException(
                    "Production requires OTP_HASH_PEPPER with at least 32 characters");
        }
    }
}
