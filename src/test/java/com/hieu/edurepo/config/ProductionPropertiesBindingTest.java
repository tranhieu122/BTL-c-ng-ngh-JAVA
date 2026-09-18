package com.hieu.edurepo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionPropertiesBindingTest {

    @Test
    void productionHardeningPropertiesAreBound() {
        new ApplicationContextRunner()
                .withInitializer(context -> TestPropertySourceUtils.addPropertiesFilesToEnvironment(context,
                        "file:src/main/resources/application.properties",
                        "file:src/main/resources/application-prod.properties"))
                .run(context -> {
                    Binder binder = Binder.get(context.getEnvironment());
                    assertThat(binder.bind("server.servlet.session.cookie.secure", Boolean.class).orElse(false)).isTrue();
                    assertThat(binder.bind("server.servlet.session.cookie.http-only", Boolean.class).orElse(false)).isTrue();
                    assertThat(binder.bind("server.servlet.session.cookie.same-site", String.class).orElse("")).isEqualTo("lax");
                    assertThat(binder.bind("server.servlet.session.timeout", Duration.class).orElseThrow(AssertionError::new))
                            .isEqualTo(Duration.ofMinutes(30));
                    assertThat(binder.bind("spring.mail.properties.mail.smtp.connectiontimeout", Integer.class).orElseThrow(AssertionError::new))
                            .isEqualTo(5000);
                    assertThat(binder.bind("spring.mail.properties.mail.smtp.timeout", Integer.class).orElseThrow(AssertionError::new))
                            .isEqualTo(5000);
                    assertThat(binder.bind("spring.mail.properties.mail.smtp.writetimeout", Integer.class).orElseThrow(AssertionError::new))
                            .isEqualTo(5000);
                    assertThat(binder.bind("server.shutdown", String.class).orElse("")).isEqualTo("graceful");
                    assertThat(binder.bind("server.forward-headers-strategy", String.class).orElse("")).isEqualTo("framework");
                    assertThat(binder.bind("spring.datasource.hikari.connection-timeout", Integer.class).orElse(0))
                            .isEqualTo(5000);
                    assertThat(binder.bind("spring.datasource.hikari.maximum-pool-size", Integer.class).orElse(0))
                            .isEqualTo(10);
                    assertThat(binder.bind("spring.datasource.hikari.minimum-idle", Integer.class).orElse(0))
                            .isEqualTo(2);
                    assertThat(binder.bind("spring.flyway.baseline-on-migrate", Boolean.class).orElse(true)).isFalse();
                    assertThat(binder.bind("spring.jpa.hibernate.ddl-auto", String.class).orElse("")).isEqualTo("validate");
                });
    }

    @Test
    void developmentDoesNotRequireSecureCookieOverHttp() {
        new ApplicationContextRunner()
                .withInitializer(context -> TestPropertySourceUtils.addPropertiesFilesToEnvironment(context,
                        "file:src/main/resources/application.properties"))
                .run(context -> assertThat(Binder.get(context.getEnvironment())
                        .bind("server.servlet.session.cookie.secure", Boolean.class).orElse(true)).isFalse());
    }
}
