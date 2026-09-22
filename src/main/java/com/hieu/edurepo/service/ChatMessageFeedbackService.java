package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ChatFeedbackRequest;
import com.hieu.edurepo.entity.ChatMessageFeedback;

/**
 * Service quản lý phản hồi đánh giá chất lượng câu trả lời của trợ lý AI.
 */
public interface ChatMessageFeedbackService {

    /**
     * Ghi nhận hoặc cập nhật (Upsert) đánh giá của người dùng.
     * Đảm bảo một người dùng / IP chỉ có 1 đánh giá hiện hành cho mỗi câu trả lời (tránh duplicate).
     *
     * @param request Dữ liệu đánh giá (messageId, rating, reason, comment)
     * @param userId ID người dùng đã đăng nhập (hoặc null nếu là khách vãng lai)
     * @param clientIp Địa chỉ IP của client
     * @return Entity phản hồi đã lưu
     */
    ChatMessageFeedback recordFeedback(ChatFeedbackRequest request, Long userId, String clientIp);
}
