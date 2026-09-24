package com.hieu.edurepo.service;

/**
 * Service quản lý bộ nhớ ngữ cảnh hội thoại đa lượt (Multi-turn Memory) cho EduBot.
 */
public interface ConversationMemoryService {

    /**
     * Lấy chuỗi biểu diễn lịch sử trò chuyện (Conversation History) gần nhất của session để gửi cho LLM.
     * Áp dụng Sliding Window: 3-5 turns gần nhất (tối đa 6-10 tin nhắn), loại bỏ rác metadata,
     * giới hạn ký tự/token nghiêm ngặt để không làm tràn context window của mô hình.
     *
     * @param sessionId ID phiên trò chuyện
     * @param maxTurns Số lượt trao đổi tối đa cần lấy (mặc định 4)
     * @return Chuỗi ngữ cảnh lịch sử hội thoại chuẩn hóa, hoặc chuỗi rỗng nếu phiên mới
     */
    String getConversationHistoryForPrompt(Long sessionId, int maxTurns);
}
