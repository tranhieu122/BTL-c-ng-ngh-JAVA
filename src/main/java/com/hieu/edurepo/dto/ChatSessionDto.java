package com.hieu.edurepo.dto;

import java.time.LocalDateTime;

/**
 * DTO đại diện cho phiên trò chuyện EduBot trả về cho frontend hiển thị trên sidebar.
 */
public record ChatSessionDto(
        Long id,
        String title,
        String scopeType,
        Long scopedDocumentId,
        String scopedDocumentTitle,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String lastMessageSnippet
) {}
