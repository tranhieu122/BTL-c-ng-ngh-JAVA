package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.DocumentReportReason;
import com.hieu.edurepo.enums.DocumentReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ báo cáo vi phạm (report) của người dùng đối với một tài liệu.
 *
 * <p>Người dùng có thể báo cáo tài liệu vì nhiều lý do: nội dung không phù hợp,
 * vi phạm bản quyền, thông tin sai lệch, v.v. Mỗi người dùng chỉ được báo cáo
 * một lý do cụ thể cho mỗi tài liệu (unique constraint theo document + reporter + reason).</p>
 *
 * <p>Admin/thủ thư sẽ xem xét và xử lý các báo cáo (RESOLVED hoặc DISMISSED).</p>
 *
 * <p>Bảng CSDL: {@code document_reports}</p>
 */
@Entity
@Table(name = "document_reports", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_report_reason", columnNames = {"document_id", "reporter_id", "reason"}))
public class DocumentReport {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tài liệu bị báo cáo. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Người dùng gửi báo cáo. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    /**
     * Lý do báo cáo (enum): vi phạm bản quyền, nội dung không phù hợp,
     * thông tin sai, trùng lặp, v.v.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DocumentReportReason reason;

    /** Mô tả chi tiết thêm của người báo cáo (tùy chọn). */
    @Column(length = 1000)
    private String description;

    /**
     * Trạng thái xử lý báo cáo: PENDING, RESOLVED, DISMISSED.
     * Mặc định PENDING khi mới tạo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentReportStatus status = DocumentReportStatus.PENDING;

    /** Admin/thủ thư đã xử lý báo cáo (null khi chưa xử lý). */
    @ManyToOne
    @JoinColumn(name = "handled_by")
    private User handledBy;

    /** Thời điểm báo cáo được xử lý (null khi chưa xử lý). */
    private LocalDateTime handledAt;

    /** Thời điểm báo cáo được tạo, không thể thay đổi. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Thời điểm báo cáo được cập nhật gần nhất. */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Callback JPA: gán thời điểm tạo và cập nhật khi persist lần đầu.
     */
    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    /**
     * Callback JPA: cập nhật updatedAt mỗi khi entity được sửa.
     */
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID báo cáo. */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    /** @return Tài liệu bị báo cáo. */
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    /** @return Người báo cáo. */
    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }

    /** @return Lý do báo cáo. */
    public DocumentReportReason getReason() { return reason; }
    public void setReason(DocumentReportReason reason) { this.reason = reason; }

    /** @return Mô tả chi tiết báo cáo. */
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** @return Trạng thái xử lý báo cáo. */
    public DocumentReportStatus getStatus() { return status; }
    public void setStatus(DocumentReportStatus status) { this.status = status; }

    /** @return Người xử lý báo cáo. */
    public User getHandledBy() { return handledBy; }
    public void setHandledBy(User handledBy) { this.handledBy = handledBy; }

    /** @return Thời điểm xử lý báo cáo. */
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }

    /** @return Thời điểm tạo báo cáo. */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** @return Thời điểm cập nhật báo cáo gần nhất. */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
