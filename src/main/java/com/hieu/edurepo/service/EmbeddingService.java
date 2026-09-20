package com.hieu.edurepo.service;

import java.util.List;

/**
 * Service interface chịu trách nhiệm chuyển đổi văn bản thành vector embedding.
 *
 * <p>Vector embedding được dùng trong pipeline RAG để tính độ tương đồng ngữ nghĩa
 * giữa câu hỏi của người dùng và các đoạn văn bản (chunks) trong tài liệu.</p>
 *
 * <p>Implementation mặc định: {@code EmbeddingServiceImpl} gọi API OpenAI
 * ({@code text-embedding-3-small}). Khi không cấu hình API key, service sẽ trả về
 * vector rỗng và {@code isAvailable()} sẽ là {@code false}.</p>
 */
public interface EmbeddingService {

    /**
     * Tạo vector embedding cho một đoạn văn bản.
     *
     * @param text Văn bản đầu vào cần embedding.
     * @return Vector embedding dạng danh sách số thực ({@code List<Double>}).
     *         Trả về danh sách rỗng nếu service không khả dụng hoặc xảy ra lỗi.
     */
    List<Double> embedText(String text);

    /**
     * Tạo vector embedding cho nhiều đoạn văn bản trong một lần gọi API (batch).
     * Hiệu quả hơn gọi {@link #embedText(String)} nhiều lần riêng lẻ.
     *
     * @param texts Danh sách văn bản đầu vào.
     * @return Danh sách vector embedding tương ứng, giữ nguyên thứ tự với {@code texts}.
     */
    List<List<Double>> embedBatch(List<String> texts);

    /**
     * Kiểm tra xem dịch vụ embedding có sẵn sàng để sử dụng không.
     * Thường là {@code false} khi chưa cấu hình API key hoặc kết nối API thất bại.
     *
     * @return {@code true} nếu có thể gọi API embedding thành công.
     */
    boolean isAvailable();
}
