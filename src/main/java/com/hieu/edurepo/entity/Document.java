package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.LicenseType;
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

import java.time.LocalDateTime;

/**
 * Entity đại diện cho một tài liệu học liệu trong hệ thống EduRepo.
 *
 * <p>Mỗi tài liệu có thể trải qua vòng đời từ DRAFT → PENDING_REVIEW → APPROVED/REJECTED.
 * Tài liệu lưu trữ thông tin về file vật lý, metadata học thuật (loại tài nguyên,
 * cấp học, bản quyền), người tạo, danh mục, khoa/phòng ban, và các thống kê
 * lượt xem/tải xuống.</p>
 *
 * <p>Bảng CSDL: {@code documents}</p>
 *
 * <p>Sử dụng {@code @DynamicUpdate} để chỉ cập nhật những cột thực sự thay đổi,
 * tránh ghi đè không cần thiết khi có nhiều luồng đồng thời.</p>
 */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "documents")
public class Document {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tiêu đề tài liệu, bắt buộc điền. */
    @Column(nullable = false)
    private String title;

    /** Mô tả chi tiết nội dung tài liệu (lưu dạng TEXT). */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Tóm tắt tự động sinh bởi AI (hoặc do người dùng nhập).
     * Được sử dụng để hiển thị nhanh và phục vụ pipeline RAG.
     */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Danh sách từ khóa, phân cách bởi dấu phẩy, dùng cho tìm kiếm lexical. */
    @Column(length = 1000)
    private String keywords;

    /**
     * Mã ngôn ngữ của tài liệu theo chuẩn ISO 639-1 (vd: "vi", "en").
     * Mặc định là "vi" (tiếng Việt).
     */
    @Column(length = 10)
    private String languageCode = "vi";

    /** Loại tài nguyên học tập (giáo trình, bài giảng, luận văn, v.v.). */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private LearningResourceType learningResourceType = LearningResourceType.OTHER;

    /** Cấp độ giáo dục phù hợp (đại học, cao học, tất cả cấp, v.v.). */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EducationLevel educationLevel = EducationLevel.ALL_LEVELS;

    /** Loại giấy phép bản quyền áp dụng cho tài liệu. */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private LicenseType licenseType = LicenseType.ALL_RIGHTS_RESERVED;

    /** Tên tác giả gốc của tài liệu (có thể khác người upload). */
    private String authorName;

    /** Tên file gốc khi upload (dùng để hiển thị cho người dùng). */
    private String fileName;

    /** Đường dẫn lưu trữ file vật lý trên server (relative hoặc S3 key). */
    private String filePath;

    /**
     * Loại MIME hoặc phần mở rộng file (vd: "application/pdf", "docx").
     * Dùng để xác định trình xử lý phù hợp khi trích xuất văn bản.
     */
    private String fileType;

    /** Kích thước file tính bằng byte. */
    private Long fileSize;

    /**
     * Trạng thái hiện tại của tài liệu trong workflow duyệt.
     * Mặc định là DRAFT khi mới tạo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.DRAFT;

    /** Danh mục chủ đề mà tài liệu thuộc về. */
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    /** Khoa/phòng ban sở hữu hoặc phụ trách tài liệu. */
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    /** Người dùng đã tạo/upload tài liệu này. */
    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    /** Thời điểm tài liệu được tạo, không thay đổi sau khi lưu. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Thời điểm tài liệu được cập nhật gần nhất, tự động cập nhật qua {@code @PreUpdate}. */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Thời điểm người dùng nộp tài liệu để duyệt (submit). */
    private LocalDateTime submittedAt;

    /** Thời điểm tài liệu được duyệt và công bố (publish). */
    private LocalDateTime publishedAt;

    /** Số lượt xem lũy tiến của tài liệu. */
    @Column(nullable = false)
    private long viewCount;

    /** Số lượt tải xuống lũy tiến của tài liệu. */
    @Column(nullable = false)
    private long downloadCount;

    /** Constructor mặc định yêu cầu bởi JPA. */
    public Document() {
    }

    /**
     * Callback JPA: tự động gán thời điểm tạo và cập nhật khi entity được persist lần đầu.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Callback JPA: tự động cập nhật trường {@code updatedAt} mỗi khi entity được sửa.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID duy nhất của tài liệu. */
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** @return Tiêu đề tài liệu. */
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /** @return Mô tả chi tiết tài liệu. */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** @return Tóm tắt nội dung tài liệu. */
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    /** @return Từ khóa tìm kiếm. */
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    /** @return Mã ngôn ngữ (ISO 639-1). */
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }

    /** @return Loại tài nguyên học tập. */
    public LearningResourceType getLearningResourceType() { return learningResourceType; }
    public void setLearningResourceType(LearningResourceType learningResourceType) { this.learningResourceType = learningResourceType; }

    /** @return Cấp độ giáo dục phù hợp. */
    public EducationLevel getEducationLevel() { return educationLevel; }
    public void setEducationLevel(EducationLevel educationLevel) { this.educationLevel = educationLevel; }

    /** @return Loại giấy phép bản quyền. */
    public LicenseType getLicenseType() { return licenseType; }
    public void setLicenseType(LicenseType licenseType) { this.licenseType = licenseType; }

    /** @return Tên tác giả gốc. */
    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    /** @return Tên file gốc. */
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** @return Đường dẫn lưu trữ file. */
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /** @return Loại MIME/phần mở rộng file. */
    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    /** @return Kích thước file (byte). */
    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    /** @return Trạng thái tài liệu trong workflow. */
    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    /** @return Danh mục tài liệu. */
    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    /** @return Khoa/phòng ban phụ trách. */
    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    /** @return Người tạo tài liệu. */
    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    /** @return Thời điểm tạo tài liệu. */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** @return Thời điểm cập nhật gần nhất. */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** @return Thời điểm nộp duyệt. */
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    /** @return Thời điểm được công bố. */
    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    /** @return Số lượt xem. */
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }

    /** @return Số lượt tải xuống. */
    public long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(long downloadCount) { this.downloadCount = downloadCount; }
}
