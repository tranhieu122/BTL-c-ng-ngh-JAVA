package com.hieu.edurepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Lớp cấu hình tập trung cho tích hợp OpenAI API trong hệ thống EduRepo.
 *
 * <p>Đọc giá trị từ file {@code application.properties} (hoặc môi trường) với tiền tố {@code openai.*}.</p>
 *
 * <p>Ví dụ cấu hình:</p>
 * <pre>
 * openai.api-key=sk-...
 * openai.model=gpt-4o-mini
 * openai.embedding-model=text-embedding-3-small
 * openai.base-url=https://api.openai.com/v1
 * openai.timeout-seconds=45
 * </pre>
 *
 * <p>Khi {@code apiKey} không được đặt, phương thức {@link #isConfigured()} trả về {@code false}
 * và các service OpenAI sẽ hoạt động ở chế độ "offline" (không gọi API).</p>
 */
@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    /**
     * Khóa API OpenAI (bắt đầu bằng {@code sk-...}).
     * Để trống nếu chưa có API key (chạy offline mode).
     */
    private String apiKey = "";

    /**
     * Tên model LLM dùng để tạo câu trả lời chatbot.
     * Mặc định: {@code gpt-5.6-luna}.
     */
    private String model = "gemini-3.5-flash";

    /**
     * Tên model embedding dùng để chuyển văn bản thành vector.
     * Mặc định: {@code gemini-embedding-001}.
     */
    private String embeddingModel = "gemini-embedding-001";

    /**
     * URL gốc của OpenAI API (hoặc compatible endpoint).
     * Có thể trỏ đến proxy/reverse proxy nội bộ nếu cần.
     */
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/";

    /**
     * Thời gian timeout (giây) cho mỗi lần gọi API.
     * Đặt đủ lớn để tránh timeout khi LLM phản hồi chậm.
     */
    private int timeoutSeconds = 90;

    // =========================================================================
    // Getters & Setters (với validation đơn giản)
    // =========================================================================

    /** @return API key OpenAI. */
    public String getApiKey() {
        return apiKey;
    }

    /** Xóa khoảng trắng thừa ở đầu/cuối API key trước khi lưu. */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    /** @return Tên model LLM. */
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model != null && !model.isBlank() ? model.trim() : "gpt-5.6-luna";
    }

    /** @return Tên model embedding. */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel != null && !embeddingModel.isBlank() ? embeddingModel.trim() : "text-embedding-3-small";
    }

    /** @return URL gốc của OpenAI API. */
    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.openai.com/v1";
    }

    /** @return Timeout (giây) cho mỗi lần gọi API. */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 45;
    }

    /**
     * Kiểm tra xem API key đã được cấu hình hay chưa.
     *
     * @return {@code true} nếu {@code apiKey} không rỗng/blank.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
