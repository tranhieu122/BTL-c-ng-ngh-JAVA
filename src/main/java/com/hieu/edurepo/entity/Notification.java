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

/**
 * Entity lưu trữ thông báo trong hệ thống EduRepo.
 *
 * <p>Thông báo được gửi từ hệ thống hoặc từ người dùng (sender) đến một
 * người nhận cụ thể (recipient). Hỗ trợ nhiều loại thông báo (tài liệu được
 * duyệt, bị từ chối, nhận xét mới, v.v.) và các mức độ ưu tiên (NORMAL, HIGH, CRITICAL).</p>
 *
 * <p>Trường {@code eventKey} là khóa duy nhất để chống gửi thông báo trùng lặp
 * (idempotency key). Ví dụ: {@code "DOC_APPROVED_42_userId_7"}.</p>
 *
 * <p>Bảng CSDL: {@code notifications}</p>
 */
@Entity
@Table(name = "notifications")
public class Notification {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Người nhận thông báo.
     * Lazy-loaded để tránh query N+1 khi liệt kê nhiều thông báo.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /**
     * Người gửi thông báo (null nếu là thông báo tự động từ hệ thống).
     * Lazy-loaded.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    /** Tiêu đề ngắn gọn của thông báo (hiển thị trong danh sách). */
    @Column(nullable = false, length = 180)
    private String title;

    /** Nội dung đầy đủ của thông báo. */
    @Column(nullable = false, length = 1000)
    private String message;

    /**
     * Mức độ ưu tiên của thông báo: NORMAL, HIGH, CRITICAL.
     * Ảnh hưởng đến màu sắc/icon hiển thị trên UI.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationLevel level = NotificationLevel.NORMAL;

    /**
     * Loại thông báo (DOCUMENT_APPROVED, DOCUMENT_REJECTED, NEW_COMMENT, v.v.).
     * Dùng để render icon và điều hướng link phù hợp trên frontend.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    /**
     * ID tài liệu liên quan (nếu thông báo về một tài liệu cụ thể).
     * Dùng để tạo link "xem tài liệu" trực tiếp từ thông báo.
     */
    private Long documentId;

    /**
     * Khóa sự kiện duy nhất để đảm bảo idempotency (chống gửi trùng lặp).
     * Unique constraint ở cấp CSDL. Ví dụ: {@code "APPROVED_DOC_42_TO_USER_7"}.
     */
    @Column(nullable = false, unique = true, length = 180)
    private String eventKey;

    /** Trạng thái đã đọc hay chưa. */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    /** Thời điểm thông báo được tạo trong hệ thống. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Thời điểm dự kiến gửi thông báo đến người nhận.
     * Mặc định bằng {@code createdAt} (gửi ngay).
     */
    @Column(nullable = false)
    private LocalDateTime sendAt;

    /** Thời điểm người dùng đọc thông báo (null nếu chưa đọc). */
    private LocalDateTime readAt;

    /**
     * Callback JPA: khởi tạo {@code createdAt} và {@code sendAt} khi persist lần đầu.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (sendAt == null) sendAt = createdAt;
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID thông báo. */
    public Long getId() { return id; }

    /** @return Người nhận thông báo. */
    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    /**
     * Alias cho {@code getRecipient()} để tương thích với code cũ.
     * @return Người nhận thông báo.
     */
    public User getReceiver() { return recipient; }
    public void setReceiver(User receiver) { this.recipient = receiver; }

    /** @return Người gửi thông báo (null = hệ thống). */
    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    /** @return Tiêu đề thông báo. */
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    /** @return Nội dung thông báo. */
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /** Alias cho {@code getMessage()}. @return Nội dung thông báo. */
    public String getContent() { return message; }
    public void setContent(String content) { this.message = content; }

    /** @return Mức độ ưu tiên thông báo. */
    public NotificationLevel getLevel() { return level; }
    public void setLevel(NotificationLevel level) { this.level = level == null ? NotificationLevel.NORMAL : level; }

    /** @return Loại thông báo. */
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    /** @return ID tài liệu liên quan. */
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    /** @return Khóa idempotency của sự kiện. */
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }

    /** @return {@code true} nếu thông báo đã được đọc. */
    public boolean isRead() { return read; }

    /**
     * Đánh dấu thông báo đã đọc và tự động ghi nhận thời điểm đọc.
     *
     * @param read {@code true} nếu muốn đánh dấu đã đọc.
     */
    public void setRead(boolean read) {
        this.read = read;
        if (read && readAt == null) readAt = LocalDateTime.now();
    }

    /** Alias cho {@code isRead()}. @return Trạng thái đã đọc. */
    public boolean isReadStatus() { return read; }
    public void setReadStatus(boolean readStatus) { setRead(readStatus); }

    /** @return Thời điểm tạo thông báo. */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** @return Thời điểm gửi thông báo. */
    public LocalDateTime getSendAt() { return sendAt; }
    public void setSendAt(LocalDateTime sendAt) { this.sendAt = sendAt; }

    /** @return Thời điểm đọc thông báo. */
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
