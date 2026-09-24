package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.ChatSenderType;
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
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ từng tin nhắn trong một phiên hội thoại EduBot.
 * Bảng CSDL: {@code chat_message}
 */
@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_created", columnList = "session_id, created_at ASC"),
        @Index(name = "idx_chat_message_client_id", columnList = "client_message_id")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 32)
    private ChatSenderType senderType;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "citations_json", columnDefinition = "JSON")
    private String citationsJson;

    @Column(name = "feedback_rating")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer feedbackRating;

    @Column(name = "client_message_id", length = 128)
    private String clientMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ChatMessage() {
    }

    public ChatMessage(ChatSession session, ChatSenderType senderType, String content, String citationsJson, String clientMessageId) {
        this.session = session;
        this.senderType = senderType;
        this.content = content;
        this.citationsJson = citationsJson;
        this.clientMessageId = clientMessageId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChatSession getSession() {
        return session;
    }

    public void setSession(ChatSession session) {
        this.session = session;
    }

    public ChatSenderType getSenderType() {
        return senderType;
    }

    public void setSenderType(ChatSenderType senderType) {
        this.senderType = senderType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public void setCitationsJson(String citationsJson) {
        this.citationsJson = citationsJson;
    }

    public Integer getFeedbackRating() {
        return feedbackRating;
    }

    public void setFeedbackRating(Integer feedbackRating) {
        this.feedbackRating = feedbackRating;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
