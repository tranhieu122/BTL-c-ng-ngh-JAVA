package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.SubmitterRequestStatus;
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
 * Entity đại diện cho yêu cầu nâng cấp vai trò thành Submitter.
 *
 * <p>Người dùng thông thường (ROLE_USER) có thể gửi yêu cầu để được cấp
 * quyền nộp tài liệu (ROLE_SUBMITTER). Admin sẽ xem xét và phê duyệt
 * hoặc từ chối yêu cầu này.</p>
 *
 * <p>Vòng đời: PENDING → APPROVED hoặc REJECTED.</p>
 *
 * <p>Bảng CSDL: {@code submitter_requests}</p>
 */
@Entity
@Table(name = "submitter_requests")
public class SubmitterRequest {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Người dùng gửi yêu cầu nâng cấp vai trò.
     * Lazy-loaded để tránh query không cần thiết khi chỉ cần ID.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    /**
     * Lý do người dùng muốn trở thành Submitter.
     * Bắt buộc điền, tối đa 1000 ký tự.
     */
    @Column(nullable = false, length = 1000)
    private String reason;

    /**
     * Trạng thái xử lý yêu cầu: PENDING, APPROVED, REJECTED.
     * Mặc định là PENDING khi mới tạo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmitterRequestStatus status = SubmitterRequestStatus.PENDING;

    /**
     * Lý do từ chối (chỉ có giá trị khi {@code status == REJECTED}).
     * Admin phải điền để người dùng hiểu tại sao bị từ chối.
     */
    @Column(length = 1000)
    private String rejectionReason;

    /**
     * Admin đã xem xét và quyết định yêu cầu này.
     * Null khi chưa được xử lý.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    /** Thời điểm yêu cầu được tạo, không thể thay đổi sau khi lưu. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Thời điểm yêu cầu được xem xét (null khi chưa xử lý). */
    private LocalDateTime reviewedAt;

    /**
     * Callback JPA: tự động gán thời điểm tạo khi persist lần đầu.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID yêu cầu. */
    public Long getId() { return id; }

    /** @return Người gửi yêu cầu. */
    public User getRequester() { return requester; }
    public void setRequester(User requester) { this.requester = requester; }

    /** @return Lý do gửi yêu cầu. */
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    /** @return Trạng thái xử lý yêu cầu. */
    public SubmitterRequestStatus getStatus() { return status; }
    public void setStatus(SubmitterRequestStatus status) { this.status = status; }

    /** @return Lý do từ chối (nếu có). */
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    /** @return Admin đã xem xét yêu cầu. */
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }

    /** @return Thời điểm tạo yêu cầu. */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** @return Thời điểm xem xét yêu cầu. */
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
