package com.hieu.edurepo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "document_reviews", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_review_user", columnNames = {"document_id", "user_id"}))
public class DocumentReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private boolean helpful;
    @Column(nullable = false)
    private boolean easyToUnderstand;
    @Column(nullable = false)
    private boolean onTopic;
    @Column(nullable = false)
    private boolean goodFileQuality;
    @Column(nullable = false)
    private boolean hidden;
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
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isHelpful() { return helpful; }
    public void setHelpful(boolean helpful) { this.helpful = helpful; }
    public boolean isEasyToUnderstand() { return easyToUnderstand; }
    public void setEasyToUnderstand(boolean easyToUnderstand) { this.easyToUnderstand = easyToUnderstand; }
    public boolean isOnTopic() { return onTopic; }
    public void setOnTopic(boolean onTopic) { this.onTopic = onTopic; }
    public boolean isGoodFileQuality() { return goodFileQuality; }
    public void setGoodFileQuality(boolean goodFileQuality) { this.goodFileQuality = goodFileQuality; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
