package com.hieu.edurepo.observability;

import com.hieu.edurepo.enums.OtpPurpose;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Low-cardinality metrics for the few application flows that need operational alerts. */
@Component
public class OperationalMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger activeSseConnections = new AtomicInteger();

    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("edurepo.realtime.connections.active", activeSseConnections);
    }

    public void otpIssued(OtpPurpose purpose) {
        registry.counter("edurepo.otp.issued", "purpose", purpose.name()).increment();
    }

    public void otpRevoked(OtpPurpose purpose) {
        registry.counter("edurepo.otp.revoked", "purpose", purpose.name(),
                "reason", "DELIVERY_FAILED").increment();
    }

    public void otpEmail(OtpPurpose purpose, String result, MailFailureType failureType, Duration duration) {
        String failure = failureType == null ? "NONE" : failureType.name();
        registry.counter("edurepo.otp.email", "purpose", purpose.name(),
                "result", result, "failure_type", failure).increment();
        Timer.builder("edurepo.otp.email.duration")
                .tag("purpose", purpose.name()).tag("result", result)
                .register(registry).record(duration);
        if (failureType == MailFailureType.TIMEOUT) {
            registry.counter("edurepo.smtp.timeouts").increment();
        }
    }

    public void uploadValidation(Duration duration) {
        registry.timer("edurepo.upload.validation.duration").record(duration);
    }

    public void uploadSucceeded(long bytes) {
        registry.counter("edurepo.upload.completed", "result", "SUCCESS").increment();
        registry.summary("edurepo.upload.bytes").record(bytes);
    }

    public void uploadRejected(UploadFailureReason reason) {
        registry.counter("edurepo.upload.completed", "result", "REJECTED",
                "reason", reason.name()).increment();
    }

    public void uploadStorageError() {
        registry.counter("edurepo.upload.storage.errors").increment();
    }

    public void scanner(Duration duration, String result) {
        Timer.builder("edurepo.upload.scanner.duration").tag("result", result)
                .register(registry).record(duration);
        registry.counter("edurepo.upload.scanner", "result", result).increment();
    }

    public void sseOpened() {
        activeSseConnections.incrementAndGet();
        registry.counter("edurepo.realtime.connections.opened").increment();
    }

    public void sseRejected(String reason) {
        registry.counter("edurepo.realtime.connections.rejected", "reason", reason).increment();
    }

    public void sseClosed(String reason) {
        activeSseConnections.updateAndGet(current -> Math.max(0, current - 1));
        registry.counter("edurepo.realtime.connections.closed", "reason", reason).increment();
    }

    public void realtimeSnapshot(Duration duration) {
        registry.counter("edurepo.realtime.snapshots.queried").increment();
        registry.timer("edurepo.realtime.snapshot.duration").record(duration);
    }

    public void realtimeDatabaseSkipped() {
        registry.counter("edurepo.realtime.snapshots.skipped").increment();
    }

    public void realtimeSafetyRefresh() {
        registry.counter("edurepo.realtime.safety.refreshes").increment();
    }

    public void realtimeEventSent() {
        registry.counter("edurepo.realtime.events.sent").increment();
    }

    public enum MailFailureType { TIMEOUT, CONFIGURATION, MAIL_PROVIDER }

    public enum UploadFailureReason {
        EMPTY,
        TOO_LARGE,
        UNSUPPORTED_EXTENSION,
        MIME_MISMATCH,
        INVALID_SIGNATURE,
        UNSAFE_FILENAME,
        UNSAFE_ARCHIVE,
        SCANNER_REJECTED,
        STORAGE_ERROR
    }
}
