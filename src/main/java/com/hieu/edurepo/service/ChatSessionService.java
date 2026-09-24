package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ChatMessageDto;
import com.hieu.edurepo.dto.ChatSessionDto;
import com.hieu.edurepo.dto.CreateChatSessionRequest;
import com.hieu.edurepo.dto.GuestMigrationRequest;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.entity.ChatMessage;
import com.hieu.edurepo.entity.ChatSession;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service quản lý vòng đời và các thao tác nghiệp vụ của phiên trò chuyện EduBot.
 */
public interface ChatSessionService {

    /**
     * Lấy danh sách phiên trò chuyện ACTIVE của người dùng, phân trang và sắp xếp theo updatedAt DESC.
     */
    List<ChatSessionDto> getUserSessions(Long userId, Pageable pageable);

    /**
     * Tạo một phiên trò chuyện mới. Nếu chưa có tiêu đề, tự động sinh từ câu hỏi đầu tiên.
     */
    ChatSessionDto createSession(Long userId, CreateChatSessionRequest request);

    /**
     * Lấy thông tin chi tiết một phiên trò chuyện (xác thực quyền sở hữu chống IDOR).
     */
    ChatSessionDto getSession(Long sessionId, Long userId);

    /**
     * Lấy danh sách tin nhắn của một phiên trò chuyện (xác thực quyền sở hữu).
     */
    List<ChatMessageDto> getSessionMessages(Long sessionId, Long userId, Pageable pageable);

    /**
     * Đổi tên một phiên trò chuyện (xác thực quyền sở hữu).
     */
    ChatSessionDto updateSessionTitle(Long sessionId, Long userId, String title);

    /**
     * Xóa mềm (Soft delete: ACTIVE -> DELETED) một phiên trò chuyện (xác thực quyền sở hữu).
     */
    void deleteSession(Long sessionId, Long userId);

    /**
     * Tìm hoặc tự động khởi tạo phiên trò chuyện ACTIVE cho người dùng khi bắt đầu hỏi.
     */
    ChatSession getOrCreateActiveSession(Long userId, Long sessionId, String firstQuestion, Long scopedDocumentId);

    /**
     * Lưu tin nhắn của người dùng vào phiên.
     */
    ChatMessage saveUserMessage(Long sessionId, String content, String clientMessageId);

    /**
     * Lưu tin nhắn câu trả lời của trợ lý AI kèm danh sách nguồn trích dẫn citations.
     */
    ChatMessage saveAssistantMessage(Long sessionId, String content, List<RagSource> citations, String clientMessageId);

    /**
     * Ghi nhận đánh giá phản hồi (Thumbs Up / Down) cho tin nhắn cụ thể trong session.
     */
    void updateMessageFeedback(Long sessionId, Long messageId, Long userId, Integer rating);

    /**
     * Đồng bộ các phiên hội thoại từ guest storage (localStorage) vào tài khoản người dùng sau khi đăng nhập.
     * Đảm bảo tính Idempotent (không nhân đôi dữ liệu khi retry/reload).
     *
     * @return Số lượng phiên đã import thành công
     */
    int migrateGuestSessions(Long userId, GuestMigrationRequest request);

    /**
     * Sinh tiêu đề mang tính tất định (deterministic) từ 6-8 từ đầu tiên của câu hỏi.
     */
    String generateTitleFromQuestion(String question);
}
