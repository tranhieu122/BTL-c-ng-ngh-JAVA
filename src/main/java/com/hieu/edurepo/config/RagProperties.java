package com.hieu.edurepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Đọc và chứa các cấu hình cho hệ thống RAG (Retrieval-Augmented Generation) từ file application.properties.
 * Tiền tố cấu hình: "rag.*"
 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** Cho phép bật/tắt toàn bộ tính năng RAG trong hệ thống (mặc định: true) */
    private boolean enabled = true;

    /** Số lượng đoạn tài liệu (chunks) tương đồng nhất được trích xuất từ database để đưa vào context (Top-K) */
    private int topK = 5;

    /** Độ dài ký tự tối đa của một đoạn văn bản (chunk) khi cắt nhỏ file tài liệu */
    private int chunkSize = 600;

    /** Số lượng ký tự chồng lấn (overlap) giữa các đoạn liền kề để tránh mất ngữ cảnh giữa các câu */
    private int chunkOverlap = 100;

    /** Ngưỡng độ tương đồng Cosine tối thiểu (từ 0.0 đến 1.0) để một chunk được coi là hợp lệ */
    private double similarityThreshold = 0.45;

    /** Số lượng token ngữ cảnh tối đa gửi sang mô hình ngôn ngữ lớn (LLM/OpenAI) */
    private int maxContextTokens = 3000;

    /** Ngưỡng số ký tự tối thiểu trên 1 trang PDF để xác định có text layer hay là trang scan (mặc định: 40) */
    private int minPageTextLength = 40;

    /** Bật/tắt xử lý OCR cho trang scan/hình ảnh */
    private boolean ocrEnabled = true;

    /** Ngôn ngữ nhận dạng cho OCR (ví dụ: vie+eng) */
    private String ocrLanguage = "vie+eng";

    /** Đường dẫn thư mục tessdata nếu cấu hình thủ công */
    private String tesseractDataPath = "";

    /** Bật/tắt làm sạch từ nối gạch ngang cuối dòng (hyphenation: Secu-\nrity -> Security) */
    private boolean cleanHyphenation = true;

    /** Bật/tắt bảo toàn định dạng thụt dòng cho code blocks và bảng biểu */
    private boolean preserveCodeBlocks = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK > 0 ? topK : 5;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize > 50 ? chunkSize : 600;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap >= 0 ? chunkOverlap : 100;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens > 200 ? maxContextTokens : 3000;
    }

    public int getMinPageTextLength() {
        return minPageTextLength;
    }

    public void setMinPageTextLength(int minPageTextLength) {
        this.minPageTextLength = minPageTextLength > 0 ? minPageTextLength : 40;
    }

    public boolean isOcrEnabled() {
        return ocrEnabled;
    }

    public void setOcrEnabled(boolean ocrEnabled) {
        this.ocrEnabled = ocrEnabled;
    }

    public String getOcrLanguage() {
        return ocrLanguage;
    }

    public void setOcrLanguage(String ocrLanguage) {
        this.ocrLanguage = ocrLanguage != null ? ocrLanguage : "vie+eng";
    }

    public String getTesseractDataPath() {
        return tesseractDataPath;
    }

    public void setTesseractDataPath(String tesseractDataPath) {
        this.tesseractDataPath = tesseractDataPath;
    }

    public boolean isCleanHyphenation() {
        return cleanHyphenation;
    }

    public void setCleanHyphenation(boolean cleanHyphenation) {
        this.cleanHyphenation = cleanHyphenation;
    }

    public boolean isPreserveCodeBlocks() {
        return preserveCodeBlocks;
    }

    public void setPreserveCodeBlocks(boolean preserveCodeBlocks) {
        this.preserveCodeBlocks = preserveCodeBlocks;
    }
}
