package com.hieu.edurepo.observability;

import com.hieu.edurepo.enums.OtpPurpose;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử thu thập chỉ số vận hành Micrometer (Operational Metrics Test).
 * Kiểm tra các thẻ đo lường (tags) có giới hạn hữu hạn, an toàn và không làm rò rỉ thông tin cá nhân (PII).
 */
class OperationalMetricsTest {
    @Test
    void otpMetricsContainOnlyFiniteOperationalTags() {
        var registry = new SimpleMeterRegistry();
        var metrics = new OperationalMetrics(registry);

        metrics.otpIssued(OtpPurpose.REGISTER);
        metrics.otpEmail(OtpPurpose.REGISTER, "FAILED",
                OperationalMetrics.MailFailureType.TIMEOUT, Duration.ofMillis(25));
        metrics.otpRevoked(OtpPurpose.REGISTER);

        assertEquals(1.0, registry.get("edurepo.smtp.timeouts").counter().count());
        Set<String> allowedKeys = Set.of("purpose", "result", "failure_type", "reason");
        assertTrue(registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("edurepo.otp")
                        || meter.getId().getName().startsWith("edurepo.smtp"))
                .flatMap(meter -> meter.getId().getTags().stream())
                .allMatch(tag -> allowedKeys.contains(tag.getKey())));
        assertTrue(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> tag.getValue().contains("@") || tag.getKey().matches(".*(email|otp|session|user).*")));
    }
}
