package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;

/**
 * Service interface cho AI Chatbot hỗ trợ tra cứu tài liệu (Document Assistant).
 *
 * <p>Đây là điểm vào chính của pipeline RAG (Retrieval-Augmented Generation):</p>
 * <ol>
 *   <li>Nhận câu hỏi từ người dùng.</li>
 *   <li>Truy xuất các chunk tài liệu liên quan qua {@code RetrievalService}.</li>
 *   <li>Xây dựng context prompt từ các chunk đã lấy được.</li>
 *   <li>Gọi LLM (OpenAI) để tạo câu trả lời có trích dẫn nguồn.</li>
 *   <li>Trả về câu trả lời kèm danh sách tài liệu tham khảo.</li>
 * </ol>
 *
 * <p>Giới hạn nghiệp vụ:</p>
 * <ul>
 *   <li>{@link #MAX_MESSAGE_LENGTH} – Độ dài tối đa của câu hỏi người dùng (200 ký tự).</li>
 *   <li>{@link #MAX_RESULTS} – Số tài liệu tham khảo tối đa trả về (5 tài liệu).</li>
 * </ul>
 *
 * <p>Implementation: {@code DocumentAssistantServiceImpl}.</p>
 */
public interface DocumentAssistantService {

    /**
     * Độ dài tối đa của tin nhắn người dùng gửi đến chatbot (tính bằng ký tự).
     * Câu hỏi vượt quá giới hạn này sẽ bị từ chối ở tầng controller.
     */
    int MAX_MESSAGE_LENGTH = 200;

    /**
     * Số lượng tài liệu tham khảo tối đa được trả về trong câu trả lời.
     */
    int MAX_RESULTS = 5;

    /**
     * Xử lý câu hỏi của người dùng theo ngữ cảnh mặc định (không có lịch sử hội thoại).
     *
     * @param message Câu hỏi của người dùng (tối đa {@link #MAX_MESSAGE_LENGTH} ký tự).
     * @return Câu trả lời từ AI kèm danh sách tài liệu tham khảo.
     */
    DocumentAssistantResponse respond(String message);

    /**
     * Xử lý câu hỏi của người dùng với ngữ cảnh hội thoại được cung cấp.
     *
     * @param message Câu hỏi của người dùng.
     * @param context Ngữ cảnh bổ sung: lịch sử hội thoại, ID tài liệu đang xem, v.v.
     * @return Câu trả lời từ AI kèm danh sách tài liệu tham khảo.
     */
    DocumentAssistantResponse respond(String message, DocumentAssistantContext context);

    /**
     * Xử lý câu hỏi của người dùng với ngữ cảnh hội thoại và phạm vi giới hạn trong tài liệu (Scoped PDF Mode).
     *
     * @param message Câu hỏi của người dùng.
     * @param context Ngữ cảnh hội thoại.
     * @param scopedDocumentId ID tài liệu cần giới hạn phạm vi (nếu có).
     * @return Câu trả lời từ AI kèm danh sách tài liệu tham khảo.
     */
    DocumentAssistantResponse respond(String message, DocumentAssistantContext context, Long scopedDocumentId);

    /**
     * Xử lý câu hỏi và truyền phát phản hồi trực tiếp tới người dùng qua Server-Sent Events (SSE).
     *
     * @param message Câu hỏi của người dùng.
     * @param context Ngữ cảnh hội thoại.
     * @param scopedDocumentId ID tài liệu giới hạn phạm vi (hoặc null nếu tra cứu toàn hệ thống).
     * @param emitter Đối tượng SseEmitter truyền phát sự kiện xuống trình duyệt.
     */
    void streamResponse(String message, DocumentAssistantContext context, Long scopedDocumentId, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter);
}

