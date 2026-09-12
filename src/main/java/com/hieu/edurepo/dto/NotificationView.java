package com.hieu.edurepo.dto;

import com.hieu.edurepo.entity.Notification;
import com.hieu.edurepo.enums.NotificationLevel;
import com.hieu.edurepo.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationView(Long id, String title, String message, NotificationType type,
                               Long documentId, boolean read, LocalDateTime createdAt,
                               String senderName, String receiverName, NotificationLevel level,
                               LocalDateTime sendAt, LocalDateTime readAt) {
    public static NotificationView from(Notification notification) {
        String senderName = notification.getSender() == null
                ? "Hệ thống"
                : displayName(notification.getSender().getFullName(), notification.getSender().getEmail());
        String receiverName = notification.getRecipient() == null
                ? ""
                : displayName(notification.getRecipient().getFullName(), notification.getRecipient().getEmail());
        return new NotificationView(notification.getId(), notification.getTitle(), notification.getMessage(),
                notification.getType(), notification.getDocumentId(), notification.isRead(), notification.getCreatedAt(),
                senderName, receiverName, notification.getLevel(), notification.getSendAt(), notification.getReadAt());
    }

    public String content() {
        return message;
    }

    public boolean readStatus() {
        return read;
    }

    private static String displayName(String fullName, String email) {
        return fullName == null || fullName.isBlank() ? email : fullName;
    }
}
