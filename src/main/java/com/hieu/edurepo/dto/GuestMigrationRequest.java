package com.hieu.edurepo.dto;

import java.util.List;

/**
 * Request chuyển giao/đồng bộ (migrate) các cuộc trò chuyện từ guest storage (localStorage) vào tài khoản người dùng sau khi đăng nhập.
 */
public record GuestMigrationRequest(
        List<GuestSessionItem> sessions
) {
    public record GuestSessionItem(
            String clientSessionId,
            String title,
            String scopeType,
            Long scopedDocumentId,
            List<GuestMessageItem> messages
    ) {}

    public record GuestMessageItem(
            String senderType,
            String content,
            List<RagSource> citations,
            Integer rating,
            String clientMessageId
    ) {}
}
