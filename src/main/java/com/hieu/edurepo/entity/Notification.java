package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.NotificationType;
import com.hieu.edurepo.enums.NotificationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationLevel level = NotificationLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    private Long documentId;

    @Column(nullable = false, unique = true, length = 180)
    private String eventKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime sendAt;

    private LocalDateTime readAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (sendAt == null) sendAt = createdAt;
    }

    public Long getId() { return id; }
    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }
    public User getReceiver() { return recipient; }
    public void setReceiver(User receiver) { this.recipient = receiver; }
    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getContent() { return message; }
    public void setContent(String content) { this.message = content; }
    public NotificationLevel getLevel() { return level; }
    public void setLevel(NotificationLevel level) { this.level = level == null ? NotificationLevel.NORMAL : level; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) {
        this.read = read;
        if (read && readAt == null) readAt = LocalDateTime.now();
    }
    public boolean isReadStatus() { return read; }
    public void setReadStatus(boolean readStatus) { setRead(readStatus); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSendAt() { return sendAt; }
    public void setSendAt(LocalDateTime sendAt) { this.sendAt = sendAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
