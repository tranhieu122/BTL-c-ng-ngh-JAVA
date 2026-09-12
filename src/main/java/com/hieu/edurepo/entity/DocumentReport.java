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

@Entity
@Table(name = "document_reports", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_report_reason", columnNames = {"document_id", "reporter_id", "reason"}))
public class DocumentReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DocumentReportReason reason;
    @Column(length = 1000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentReportStatus status = DocumentReportStatus.PENDING;
    @ManyToOne
    @JoinColumn(name = "handled_by")
    private User handledBy;
    private LocalDateTime handledAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }
    public DocumentReportReason getReason() { return reason; }
    public void setReason(DocumentReportReason reason) { this.reason = reason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public DocumentReportStatus getStatus() { return status; }
    public void setStatus(DocumentReportStatus status) { this.status = status; }
    public User getHandledBy() { return handledBy; }
    public void setHandledBy(User handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
