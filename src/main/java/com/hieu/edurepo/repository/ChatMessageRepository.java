package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất dữ liệu bảng {@code chat_message}.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Lấy toàn bộ tin nhắn của session theo thứ tự thời gian tăng dần.
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * Lấy danh sách tin nhắn theo phân trang (để tối ưu hiệu năng không load hàng nghìn tin nhắn).
     */
    Page<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId, Pageable pageable);

    /**
     * Lấy N tin nhắn gần đây nhất của session theo thứ tự thời gian giảm dần (dùng cho Sliding Window Memory).
     */
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);

    /**
     * Kiểm tra tin nhắn đã tồn tại theo clientMessageId để tránh duplicate do retry mạng / double-click.
     */
    Optional<ChatMessage> findBySessionIdAndClientMessageId(Long sessionId, String clientMessageId);

    /**
     * Tìm tin nhắn theo id và sessionId (bảo vệ phân quyền khi đánh giá feedback).
     */
    Optional<ChatMessage> findByIdAndSessionId(Long id, Long sessionId);

    /**
     * Đếm tổng số tin nhắn của một session.
     */
    long countBySessionId(Long sessionId);
}
