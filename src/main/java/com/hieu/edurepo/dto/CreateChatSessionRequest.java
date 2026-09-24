package com.hieu.edurepo.dto;

import jakarta.validation.constraints.Size;

/**
 * Request tạo phiên trò chuyện EduBot mới.
 */
public record CreateChatSessionRequest(
        @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
        String title,
        String scopeType,
        Long scopedDocumentId,
        String initialQuestion
) {}
