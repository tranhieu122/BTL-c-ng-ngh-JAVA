package com.hieu.edurepo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thành phần kiểm soát tần suất truy vấn (Rate Limiter) cho API Trợ lý học liệu EduBot (/api/document-assistant).
 * 
 * Vai trò & Cơ chế bảo mật:
 * 1. Áp dụng thuật toán Sliding Window Counter (Cửa sổ trượt) theo từng địa chỉ IP / Client Identifier.
 * 2. Ngăn chặn tấn công lặp / bot spam làm bùng nổ chi phí OpenAI API (Cost Explosion) và làm cạn kiệt tài nguyên máy chủ.
 * 3. Tự động dọn dẹp các mục IP không còn hoạt động để tránh rò rỉ bộ nhớ Java Heap.
 */
@Component
public class DocumentAssistantRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentAssistantRateLimiter.class);

    /** Giới hạn số lượng request tối đa trong cửa sổ 1 phút */
    private final int maxRequestsPerMinute;

    /** Độ dài cửa sổ thời gian tính bằng miligiây (60.000 ms = 1 phút) */
    private final long windowMillis = 60_000L;

    /** Map lưu trữ lịch sử các mốc thời gian (timestamp) gửi request của từng Client IP */
    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

    /**
     * Khởi tạo Rate Limiter với tham số cấu hình tùy chỉnh từ application.properties.
     * 
     * @param maxRequestsPerMinute Số request tối đa/phút (mặc định 20 request/phút)
     */
    public DocumentAssistantRateLimiter(
            @Value("${app.assistant.rate-limit-per-minute:20}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = Math.max(1, maxRequestsPerMinute);
    }

    /**
     * Kiểm tra xem request từ Client IP có được phép thực thi hay không.
     * 
     * Quy trình xử lý:
     * 1. Loại bỏ các timestamp cũ hơn 60 giây khỏi hàng đợi của client.
     * 2. Nếu số request còn lại trong 60 giây < maxRequestsPerMinute -> Thêm timestamp hiện tại và cho phép (true).
     * 3. Ngược lại -> Chặn và trả về false.
     *
     * @param clientKey Định danh client (thường là địa chỉ IP)
     * @return true nếu request hợp lệ nằm trong hạn mức; false nếu vượt quá hạn mức cho phép.
     */
    public boolean tryAcquire(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            clientKey = "unknown";
        }

        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowMillis;

        Deque<Long> timestamps = requestHistory.computeIfAbsent(clientKey, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Loại bỏ các mốc thời gian đã trượt khỏi cửa sổ 60 giây
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            // Kiểm tra số lượng request trong cửa sổ 60 giây
            if (timestamps.size() >= maxRequestsPerMinute) {
                LOGGER.warn("Rate limit exceeded for client '{}' ({} requests in last 60s, limit is {}).",
                        clientKey, timestamps.size(), maxRequestsPerMinute);
                return false;
            }

            // Ghi nhận mốc thời gian mới
            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Tính toán số giây người dùng cần phải chờ trước khi request cũ nhất hết hạn.
     * Dùng để gửi kèm trong HTTP Header `Retry-After`.
     *
     * @param clientKey Định danh client IP
     * @return Số giây cần chờ (tối thiểu 1 giây)
     */
    public long getRetryAfterSeconds(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return 60L;
        }

        Deque<Long> timestamps = requestHistory.get(clientKey);
        if (timestamps == null) {
            return 1L;
        }

        synchronized (timestamps) {
            if (timestamps.isEmpty()) {
                return 1L;
            }
            long oldestTimestamp = timestamps.peekFirst();
            long now = Instant.now().toEpochMilli();
            long remainingMillis = (oldestTimestamp + windowMillis) - now;
            return Math.max(1L, (remainingMillis + 999L) / 1000L);
        }
    }

    /**
     * Định kỳ giải phóng các IP đã lâu không gửi yêu cầu để tránh rò rỉ bộ nhớ trong ConcurrentHashMap.
     */
    public void cleanupStaleEntries() {
        long windowStart = Instant.now().toEpochMilli() - windowMillis;
        requestHistory.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }

    /** Lấy cấu hình số lượng request tối đa trên phút */
    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }
}
