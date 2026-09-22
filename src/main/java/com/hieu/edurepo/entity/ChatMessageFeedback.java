package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.FeedbackRating;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ phản hồi đánh giá (Thumbs Up / Thumbs Down) của người dùng cho câu trả lời của trợ lý AI.
 * Bảng CSDL: {@code chat_message_feedback}
 */
@Entity
@Table(name = "chat_message_feedback", indexes = {
        @Index(name = "idx_feedback_message_id", columnList = "message_id"),
        @Index(name = "idx_feedback_user_id", columnList = "user_id")
})
public class ChatMessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 128)
    private String messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 16)
    private FeedbackRating rating;

    @Column(name = "reason", length = 64)
    private String reason;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ChatMessageFeedback() {
    }

    public ChatMessageFeedback(String messageId, User user, String clientIp, FeedbackRating rating, String reason, String comment) {
        this.messageId = messageId;
        this.user = user;
        this.clientIp = clientIp;
        this.rating = rating;
        this.reason = reason;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public FeedbackRating getRating() {
        return rating;
    }

    public void setRating(FeedbackRating rating) {
        this.rating = rating;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
