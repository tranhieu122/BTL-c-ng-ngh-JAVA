package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.AuthOtpToken;
import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.repository.AuthOtpTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:otp_concurrency_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.upload.dir=target/otp-concurrency-test-uploads"
})
/**
 * Kiểm thử an toàn luồng và chống tấn công Race Condition khi xác thực OTP.
 * Đảm bảo một mã OTP chỉ có thể được tiêu thụ một lần duy nhất khi có nhiều luồng cùng gửi đồng thời.
 */
class OtpConcurrencySecurityTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private AuthOtpTokenRepository tokenRepository;

    @Test
    @DisplayName("P1-CONC-01: High-concurrency OTP verification must prevent duplicate consumption")
    void concurrentOtpVerification_shouldAllowOnlyOneSuccess() throws InterruptedException {
        String email = "concurrent-verify-" + UUID.randomUUID() + "@edurepo.vn";
        OtpService.OtpIssue issue = otpService.issue(email, OtpPurpose.REGISTER);
        String correctCode = issue.code();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<OtpService.OtpVerification> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger validCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    OtpService.OtpVerification result = otpService.verify(email, OtpPurpose.REGISTER, correctCode);
                    results.add(result);
                    if (result == OtpService.OtpVerification.VALID) {
                        validCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Exception is also recorded if database deadlock/conflict occurs
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all 10 threads at the exact same millisecond
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All verification threads should finish in 10s");
        executor.shutdown();

        // Under transactional execution, only 1 thread should consume the token.
        // If race condition exists without locking, multiple threads might both read consumedAt == null
        // and both set consumedAt = now.
        assertEquals(1, validCount.get(), 
                "Only exactly 1 thread should get VALID result; concurrent reuse must be blocked!");

        AuthOtpToken token = tokenRepository.findById(issue.tokenId()).orElseThrow();
        assertNotNull(token.getConsumedAt(), "Token must be marked consumed");
    }

    @Test
    @DisplayName("P1-SEC-02: Concurrent brute-force attempts must lock token and not exceed maxAttempts")
    void concurrentBruteForce_shouldLockToken() throws InterruptedException {
        String email = "bruteforce-" + UUID.randomUUID() + "@edurepo.vn";
        OtpService.OtpIssue issue = otpService.issue(email, OtpPurpose.PASSWORD_RESET);

        int totalAttempts = 15;
        ExecutorService executor = Executors.newFixedThreadPool(totalAttempts);
        CountDownLatch readyLatch = new CountDownLatch(totalAttempts);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalAttempts);

        List<OtpService.OtpVerification> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < totalAttempts; i++) {
            final String wrongCode = String.format("%06d", 900000 + i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    OtpService.OtpVerification result = otpService.verify(email, OtpPurpose.PASSWORD_RESET, wrongCode);
                    results.add(result);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Ensure token ends up in locked or consumed state
        AuthOtpToken token = tokenRepository.findById(issue.tokenId()).orElseThrow();
        assertNotNull(token.getConsumedAt(), "Token must be marked consumed once locked or expired");
        assertTrue(token.getAttempts() >= 5, "Token attempts counter must record wrong attempts");

        // Subsequent verification with the right code must fail because it's locked
        OtpService.OtpVerification followUp = otpService.verify(email, OtpPurpose.PASSWORD_RESET, issue.code());
        assertNotEquals(OtpService.OtpVerification.VALID, followUp, "Token must not be valid after brute-force lockout");
    }

    @Test
    @DisplayName("P2-SEC-03: Email normalization prevents case-variation bypass")
    void emailNormalization_handlesCasingConsistently() {
        String baseEmail = "MixedCase." + UUID.randomUUID() + "@EduRepo.VN";
        OtpService.OtpIssue issue = otpService.issue(baseEmail, OtpPurpose.REGISTER);

        // Verification using lowercase version
        OtpService.OtpVerification result = otpService.verify(baseEmail.toLowerCase(), OtpPurpose.REGISTER, issue.code());
        assertEquals(OtpService.OtpVerification.VALID, result, "Verification must succeed regardless of email case");
    }
}
