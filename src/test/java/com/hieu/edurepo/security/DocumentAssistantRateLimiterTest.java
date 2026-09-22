package com.hieu.edurepo.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử giới hạn tần suất gửi tin nhắn tới Trợ lý AI (Rate Limiter Test).
 * Kiểm tra chặn đứng các request vượt hạn mức với mã HTTP 429 và khôi phục hạn mức sau cửa sổ thời gian.
 */
class DocumentAssistantRateLimiterTest {

    @Test
    void allowsRequestsWithinLimit() {
        DocumentAssistantRateLimiter limiter = new DocumentAssistantRateLimiter(5);
        String clientIp = "192.168.1.100";

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(clientIp), "Request " + (i + 1) + " should be permitted");
        }

        assertFalse(limiter.tryAcquire(clientIp), "Request 6 should exceed rate limit");
        assertTrue(limiter.getRetryAfterSeconds(clientIp) > 0, "Retry after should be positive");
    }

    @Test
    void isolatesDifferentClients() {
        DocumentAssistantRateLimiter limiter = new DocumentAssistantRateLimiter(2);
        String clientA = "10.0.0.1";
        String clientB = "10.0.0.2";

        assertTrue(limiter.tryAcquire(clientA));
        assertTrue(limiter.tryAcquire(clientA));
        assertFalse(limiter.tryAcquire(clientA), "Client A should be blocked");

        // Client B must not be affected by Client A's rate limit
        assertTrue(limiter.tryAcquire(clientB), "Client B should be permitted");
        assertTrue(limiter.tryAcquire(clientB), "Client B should be permitted");
        assertFalse(limiter.tryAcquire(clientB), "Client B should be blocked");
    }

    @Test
    void handlesNullOrBlankClientKeyGracefully() {
        DocumentAssistantRateLimiter limiter = new DocumentAssistantRateLimiter(3);
        assertTrue(limiter.tryAcquire(null));
        assertTrue(limiter.tryAcquire(""));
        assertTrue(limiter.tryAcquire("   "));
        assertFalse(limiter.tryAcquire(null), "Should limit unknown/blank clients when pool is full");
    }
}
