package com.hieu.edurepo.service;

import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.observability.OperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử dịch vụ gửi email xác thực OTP (Email Service Test).
 * Kiểm tra việc định dạng nội dung thư, gửi qua SMTP và xử lý ngoại lệ khi mất kết nối mạng.
 */
class EmailServiceTest {
    @Test
    void smtpTimeoutIsMeasuredWithoutSensitiveMetricTags() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("provider unavailable", new SocketTimeoutException("timed out")))
                .when(sender).send(any(SimpleMailMessage.class));
        var registry = new SimpleMeterRegistry();
        var service = new EmailService(provider, "mailer@example.test", "secret", true,
                new OperationalMetrics(registry));

        assertFalse(service.sendOtp("person@example.test", "123456", OtpPurpose.PASSWORD_RESET));

        assertTrue(registry.get("edurepo.smtp.timeouts").counter().count() > 0);
        assertTrue(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> tag.getValue().contains("person@example.test")
                        || tag.getValue().contains("123456") || tag.getValue().contains("secret")));
    }
}
