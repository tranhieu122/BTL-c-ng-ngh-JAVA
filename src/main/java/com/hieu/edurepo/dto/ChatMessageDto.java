package com.hieu.edurepo.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO chứa thông tin tin nhắn trong phiên trò chuyện EduBot, bao gồm metadata nguồn trích dẫn đầy đủ.
 */
public record ChatMessageDto(
        Long id,
        Long sessionId,
        String senderType,
        String content,
        List<RagSource> citations,
        Integer feedbackRating,
        String clientMessageId,
        LocalDateTime createdAt
) {}
