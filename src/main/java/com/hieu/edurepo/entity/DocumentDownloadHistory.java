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

@Entity
@Table(name = "document_download_history", uniqueConstraints =
        @UniqueConstraint(name = "uk_download_history_user_document", columnNames = {"user_id", "document_id"}))
public class DocumentDownloadHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private LocalDateTime downloadedAt;

    @Column(nullable = false)
    private long downloadCount = 1;

    @PrePersist
    void onCreate() {
        if (downloadedAt == null) downloadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public LocalDateTime getDownloadedAt() { return downloadedAt; }
    public void setDownloadedAt(LocalDateTime downloadedAt) { this.downloadedAt = downloadedAt; }
    public long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(long downloadCount) { this.downloadCount = downloadCount; }
}
