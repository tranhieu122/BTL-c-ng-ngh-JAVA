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

/**
 * Entity lưu trữ đánh giá (review/rating) của người dùng cho một tài liệu.
 *
 * <p>Mỗi người dùng chỉ được đánh giá một tài liệu một lần (ràng buộc unique
 * theo cặp {@code document_id, user_id}).</p>
 *
 * <p>Đánh giá bao gồm điểm số (1-5), nhận xét văn bản, và các tiêu chí
 * định tính: hữu ích, dễ hiểu, đúng chủ đề, chất lượng file tốt.</p>
 *
 * <p>Bảng CSDL: {@code document_reviews}</p>
 */
@Entity
@Table(name = "document_reviews", uniqueConstraints =
        @UniqueConstraint(name = "uk_document_review_user", columnNames = {"document_id", "user_id"}))
public class DocumentReview {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tài liệu được đánh giá. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Người dùng thực hiện đánh giá. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Điểm đánh giá tổng thể từ 1 (tệ nhất) đến 5 (tốt nhất).
     * Được tính vào điểm trung bình hiển thị trên trang tài liệu.
     */
    @Column(nullable = false)
    private int rating;

    /** Nhận xét tự do của người đánh giá (tùy chọn). */
    @Column(length = 500)
    private String comment;

    /** Tài liệu này có hữu ích cho việc học không? */
    @Column(nullable = false)
    private boolean helpful;

    /** Nội dung có dễ hiểu không? */
    @Column(nullable = false)
    private boolean easyToUnderstand;

    /** Tài liệu có đúng chủ đề/danh mục không? */
    @Column(nullable = false)
    private boolean onTopic;

    /** Chất lượng file (độ phân giải, định dạng) có tốt không? */
    @Column(nullable = false)
    private boolean goodFileQuality;

    /**
     * Đánh giá có bị ẩn bởi kiểm duyệt viên hay không.
     * Khi {@code true}, đánh giá sẽ không hiển thị cho người dùng thông thường.
     */
    @Column(nullable = false)
    private boolean hidden;

    /** Thời điểm tạo đánh giá, không thể sửa sau khi lưu. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Thời điểm sửa đánh giá gần nhất. */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Callback JPA: gán thời điểm tạo và cập nhật khi persist lần đầu.
     */
    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    /**
     * Callback JPA: cập nhật trường updatedAt mỗi khi entity được sửa.
     */
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID đánh giá. */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    /** @return Tài liệu được đánh giá. */
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    /** @return Người dùng đánh giá. */
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    /** @return Điểm đánh giá (1-5). */
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    /** @return Nhận xét văn bản. */
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    /** @return {@code true} nếu tài liệu hữu ích. */
    public boolean isHelpful() { return helpful; }
    public void setHelpful(boolean helpful) { this.helpful = helpful; }

    /** @return {@code true} nếu dễ hiểu. */
    public boolean isEasyToUnderstand() { return easyToUnderstand; }
    public void setEasyToUnderstand(boolean easyToUnderstand) { this.easyToUnderstand = easyToUnderstand; }

    /** @return {@code true} nếu đúng chủ đề. */
    public boolean isOnTopic() { return onTopic; }
    public void setOnTopic(boolean onTopic) { this.onTopic = onTopic; }

    /** @return {@code true} nếu chất lượng file tốt. */
    public boolean isGoodFileQuality() { return goodFileQuality; }
    public void setGoodFileQuality(boolean goodFileQuality) { this.goodFileQuality = goodFileQuality; }

    /** @return {@code true} nếu đánh giá bị ẩn. */
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    /** @return Thời điểm tạo đánh giá. */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** @return Thời điểm cập nhật đánh giá gần nhất. */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
