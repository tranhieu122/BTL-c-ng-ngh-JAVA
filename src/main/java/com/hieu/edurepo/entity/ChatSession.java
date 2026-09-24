package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.ChatScopeType;
import com.hieu.edurepo.enums.ChatSessionStatus;
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
 * Entity đại diện cho một phiên hội thoại giữa người dùng và trợ lý AI EduBot.
 * Bảng CSDL: {@code chat_session}
 */
@Entity
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_chat_session_user_status_updated", columnList = "user_id, status, updated_at DESC")
})
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private ChatScopeType scopeType = ChatScopeType.GLOBAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scoped_document_id")
    private Document scopedDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ChatSessionStatus status = ChatSessionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ChatSession() {
    }

    public ChatSession(User user, String title, ChatScopeType scopeType, Document scopedDocument) {
        this.user = user;
        this.title = title;
        this.scopeType = scopeType != null ? scopeType : ChatScopeType.GLOBAL;
        this.scopedDocument = scopedDocument;
        this.status = ChatSessionStatus.ACTIVE;
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

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ChatScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ChatScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public Document getScopedDocument() {
        return scopedDocument;
    }

    public void setScopedDocument(Document scopedDocument) {
        this.scopedDocument = scopedDocument;
    }

    public ChatSessionStatus getStatus() {
        return status;
    }

    public void setStatus(ChatSessionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
