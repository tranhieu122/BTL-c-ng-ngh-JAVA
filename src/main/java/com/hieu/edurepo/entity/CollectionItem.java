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
@Table(name = "collection_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_collection_item", columnNames = {"collection_id", "document_id"}))
/**
 * Thực thể liên kết giữa tài liệu học liệu và bộ sưu tập cá nhân của người học.
 */
public class CollectionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private DocumentCollection collection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    void onCreate() { addedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DocumentCollection getCollection() { return collection; }
    public void setCollection(DocumentCollection collection) { this.collection = collection; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public LocalDateTime getAddedAt() { return addedAt; }
}
