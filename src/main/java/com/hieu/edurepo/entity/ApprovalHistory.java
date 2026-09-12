package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.ReviewAction;
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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_history")
public class ApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewAction action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private Integer contentQualityScore;
    private Integer teachingEffectivenessScore;
    private Integer easeOfUseScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public User getReviewer() {
        return reviewer;
    }

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
    }

    public ReviewAction getAction() {
        return action;
    }

    public void setAction(ReviewAction action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getContentQualityScore() { return contentQualityScore; }
    public void setContentQualityScore(Integer contentQualityScore) { this.contentQualityScore = contentQualityScore; }
    public Integer getTeachingEffectivenessScore() { return teachingEffectivenessScore; }
    public void setTeachingEffectivenessScore(Integer teachingEffectivenessScore) { this.teachingEffectivenessScore = teachingEffectivenessScore; }
    public Integer getEaseOfUseScore() { return easeOfUseScore; }
    public void setEaseOfUseScore(Integer easeOfUseScore) { this.easeOfUseScore = easeOfUseScore; }

    public boolean hasRubricScores() {
        return contentQualityScore != null || teachingEffectivenessScore != null || easeOfUseScore != null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
