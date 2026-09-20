package com.hieu.edurepo.service;

/**
 * Service interface giao tiếp với OpenAI API để tạo phản hồi AI trong hệ thống EduRepo.
 *
 * <p>Cung cấp hai chế độ gọi API:</p>
 * <ul>
 *   <li><strong>Chat đơn giản</strong>: {@link #generateChatCompletion(String, String)} – gửi system prompt
 *       và user prompt, nhận về câu trả lời dạng chuỗi.</li>
 *   <li><strong>Chat với Tool Calling</strong>: {@link #generateChatWithTools(String, String, ToolExecutorService)} –
 *       cho phép LLM gọi các "tool" (hàm) để tìm kiếm tài liệu, sau đó tổng hợp câu trả lời cuối cùng.</li>
 * </ul>
 *
 * <p>Khi không cấu hình API key hoặc API không khả dụng, {@link #isAvailable()} trả về {@code false}
 * và chatbot sẽ trả lời bằng thông báo lỗi thân thiện thay vì ném exception.</p>
 *
 * <p>Implementation: {@code OpenAIServiceImpl}.</p>
 */
public interface OpenAIService {

    /**
     * Gọi OpenAI Chat Completion API (chế độ đơn giản, không có tool calling).
     *
     * @param systemPrompt Lệnh hệ thống định nghĩa vai trò và hành vi của AI.
     * @param userPrompt   Câu hỏi hoặc yêu cầu của người dùng.
     * @return Câu trả lời dạng chuỗi văn bản từ LLM.
     */
    String generateChatCompletion(String systemPrompt, String userPrompt);

    /**
     * Gọi OpenAI Chat Completion kèm danh sách Tool Definitions và tự động phối hợp với ToolExecutorService
     * để thực thi các tool_calls (nếu LLM yêu cầu), sau đó trả về câu trả lời tổng hợp cuối cùng.
     *
     * <p>Luồng xử lý Tool Calling:</p>
     * <ol>
     *   <li>Gửi prompt + danh sách tool definitions đến OpenAI.</li>
     *   <li>Nếu LLM quyết định gọi tool → trích xuất tool_calls từ response.</li>
     *   <li>Thực thi từng tool qua {@code toolExecutor}, thu thập kết quả.</li>
     *   <li>Gửi lại kết quả tool vào context để LLM tổng hợp câu trả lời cuối.</li>
     * </ol>
     *
     * @param systemPrompt System instructions cho LLM.
     * @param userPrompt   Câu hỏi hoặc yêu cầu của người dùng.
     * @param toolExecutor Dịch vụ thực thi tool (tìm kiếm tài liệu, v.v.).
     * @return Đối tượng {@code ToolChatResponse} gồm câu trả lời và danh sách nguồn tài liệu (RAG sources).
     */
    com.hieu.edurepo.dto.ToolChatResponse generateChatWithTools(String systemPrompt, String userPrompt, ToolExecutorService toolExecutor);

    /**
     * Kiểm tra xem OpenAI API có khả dụng hay không.
     *
     * @return {@code true} nếu API key được cấu hình hợp lệ và service sẵn sàng.
     */
    boolean isAvailable();
}
