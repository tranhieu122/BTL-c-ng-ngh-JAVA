package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.ReviewAction;
import com.hieu.edurepo.enums.DocumentStatus;
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

/**
 * Entity lưu trữ lịch sử duyệt (approval history) của từng tài liệu.
 *
 * <p>Mỗi lần kiểm duyệt viên thực hiện một hành động trên tài liệu
 * (APPROVE, REJECT, REQUEST_REVISION, v.v.) sẽ tạo ra một bản ghi mới,
 * ghi lại: ai duyệt, hành động gì, trạng thái trước/sau, nhận xét và điểm rubric.</p>
 *
 * <p>Dữ liệu này không thể sửa hay xóa — chỉ thêm mới (append-only)
 * để đảm bảo tính minh bạch và truy vết đầy đủ trong quy trình công bố tài liệu.</p>
 *
 * <p>Bảng CSDL: {@code approval_history}</p>
 */
@Entity
@Table(name = "approval_history")
public class ApprovalHistory {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tài liệu được duyệt/từ chối. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Kiểm duyệt viên thực hiện hành động. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    /**
     * Hành động duyệt được thực hiện:
     * APPROVE, REJECT, REQUEST_REVISION, v.v.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewAction action;

    /** Nhận xét/lý do của kiểm duyệt viên gửi cho người nộp. */
    @Column(columnDefinition = "TEXT")
    private String comment;

    /** Trạng thái của tài liệu trước khi hành động được thực hiện. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DocumentStatus oldStatus;

    /** Trạng thái của tài liệu sau khi hành động được thực hiện. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DocumentStatus newStatus;

    /**
     * Điểm chất lượng nội dung theo rubric (thang điểm tùy cấu hình).
     * Null nếu kiểm duyệt viên không chấm điểm rubric.
     */
    private Integer contentQualityScore;

    /**
     * Điểm hiệu quả giảng dạy theo rubric.
     * Null nếu không áp dụng.
     */
    private Integer teachingEffectivenessScore;

    /**
     * Điểm dễ sử dụng theo rubric.
     * Null nếu không áp dụng.
     */
    private Integer easeOfUseScore;

    /** Thời điểm hành động duyệt được ghi nhận. Không thể thay đổi sau khi lưu. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Callback JPA: tự động gán thời điểm tạo khi persist lần đầu.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID bản ghi lịch sử duyệt. */
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** @return Tài liệu liên quan. */
    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    /** @return Kiểm duyệt viên. */
    public User getReviewer() {
        return reviewer;
    }

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
    }

    /** @return Hành động duyệt. */
    public ReviewAction getAction() {
        return action;
    }

    public void setAction(ReviewAction action) {
        this.action = action;
    }

    /** @return Nhận xét của kiểm duyệt viên. */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /** @return Trạng thái tài liệu trước khi duyệt. */
    public DocumentStatus getOldStatus() { return oldStatus; }
    public void setOldStatus(DocumentStatus oldStatus) { this.oldStatus = oldStatus; }

    /** @return Trạng thái tài liệu sau khi duyệt. */
    public DocumentStatus getNewStatus() { return newStatus; }
    public void setNewStatus(DocumentStatus newStatus) { this.newStatus = newStatus; }

    /** @return Điểm chất lượng nội dung. */
    public Integer getContentQualityScore() { return contentQualityScore; }
    public void setContentQualityScore(Integer contentQualityScore) { this.contentQualityScore = contentQualityScore; }

    /** @return Điểm hiệu quả giảng dạy. */
    public Integer getTeachingEffectivenessScore() { return teachingEffectivenessScore; }
    public void setTeachingEffectivenessScore(Integer teachingEffectivenessScore) { this.teachingEffectivenessScore = teachingEffectivenessScore; }

    /** @return Điểm dễ sử dụng. */
    public Integer getEaseOfUseScore() { return easeOfUseScore; }
    public void setEaseOfUseScore(Integer easeOfUseScore) { this.easeOfUseScore = easeOfUseScore; }

    /**
     * Kiểm tra xem bản ghi duyệt này có chứa điểm rubric hay không.
     *
     * @return {@code true} nếu có ít nhất một điểm rubric được đặt.
     */
    public boolean hasRubricScores() {
        return contentQualityScore != null || teachingEffectivenessScore != null || easeOfUseScore != null;
    }

    /** @return Thời điểm tạo bản ghi duyệt. */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
