package com.hieu.edurepo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ bookmark (đánh dấu tài liệu yêu thích) của người dùng.
 *
 * <p>Người dùng có thể đánh dấu các tài liệu để truy cập nhanh sau này.
 * Mỗi người dùng chỉ được bookmark mỗi tài liệu một lần
 * (unique constraint theo {@code user_id, document_id}).</p>
 *
 * <p>Bảng CSDL: {@code bookmarks}</p>
 */
@Entity
@Table(name = "bookmarks", uniqueConstraints =
        @UniqueConstraint(name = "uk_bookmark_user_document", columnNames = {"user_id", "document_id"}))
public class Bookmark {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Người dùng đã bookmark tài liệu.
     * Lazy-loaded để tránh query không cần thiết.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Tài liệu được đánh dấu.
     * Lazy-loaded.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Thời điểm bookmark được tạo, không thể thay đổi sau khi lưu. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Callback JPA: tự động gán thời điểm tạo khi persist.
     */
    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID bookmark. */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    /** @return Người dùng sở hữu bookmark. */
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    /** @return Tài liệu được bookmark. */
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    /** @return Thời điểm tạo bookmark. */
    public LocalDateTime getCreatedAt() { return createdAt; }
}
