package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entity ghi lại nhật ký kiểm toán (audit log) cho mọi hành động quan trọng trong hệ thống.
 *
 * <p>Mỗi bản ghi lưu lại: ai đã làm gì (action), tác động đến đối tượng nào (target),
 * kết quả thực thi (SUCCESS/FAILURE), và thông tin môi trường (IP, user-agent).</p>
 *
 * <p>Bảng CSDL: {@code audit_logs}. Dữ liệu chỉ được thêm mới, không sửa/xóa.</p>
 *
 * <p>Được ghi bởi {@code AuditLogService} qua Spring AOP hoặc gọi trực tiếp
 * trong các controller/service quan trọng như đăng nhập, duyệt tài liệu, xóa người dùng.</p>
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Thời điểm sự kiện xảy ra. Không thể cập nhật sau khi tạo. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    /** ID của người thực hiện hành động (null nếu là hành động hệ thống tự động). */
    private Long actorId;

    /** Tên hiển thị của người thực hiện (fullName hoặc "SYSTEM"). */
    @Column(length = 255)
    private String actorName;

    /** Username hoặc email của người thực hiện để truy vết dễ hơn. */
    @Column(length = 255)
    private String actorIdentifier;

    /** Danh sách vai trò của người thực hiện tại thời điểm hành động (chuỗi phân cách bởi dấu phẩy). */
    @Column(length = 255)
    private String actorRoles;

    /** Loại hành động được thực hiện (LOGIN, UPLOAD, APPROVE, DELETE, v.v.). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    /** Loại đối tượng bị tác động (DOCUMENT, USER, CATEGORY, v.v.). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditTargetType targetType;

    /** ID của đối tượng bị tác động (null nếu không áp dụng). */
    private Long targetId;

    /** Tên/tiêu đề của đối tượng bị tác động để dễ tra cứu. */
    @Column(length = 255)
    private String targetName;

    /** Mô tả chi tiết ngữ cảnh của hành động. */
    @Column(nullable = false, length = 1000)
    private String description;

    /** Kết quả thực thi: SUCCESS hoặc FAILURE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditResult result;

    /** Địa chỉ IP của người thực hiện (hỗ trợ IPv4 và IPv6). */
    @Column(length = 64)
    private String ipAddress;

    /** User-Agent của trình duyệt/client (dùng để phân tích bảo mật). */
    @Column(length = 500)
    private String userAgent;

    /**
     * Callback JPA: tự động gán thời điểm xảy ra sự kiện nếu chưa được đặt.
     */
    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID bản ghi audit. */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    /** @return Thời điểm xảy ra sự kiện. */
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    /** @return ID người thực hiện. */
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    /** @return Tên người thực hiện. */
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    /** @return Định danh (username/email) người thực hiện. */
    public String getActorIdentifier() { return actorIdentifier; }
    public void setActorIdentifier(String actorIdentifier) { this.actorIdentifier = actorIdentifier; }

    /** @return Danh sách vai trò người thực hiện. */
    public String getActorRoles() { return actorRoles; }
    public void setActorRoles(String actorRoles) { this.actorRoles = actorRoles; }

    /** @return Loại hành động. */
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }

    /** @return Loại đối tượng bị tác động. */
    public AuditTargetType getTargetType() { return targetType; }
    public void setTargetType(AuditTargetType targetType) { this.targetType = targetType; }

    /** @return ID đối tượng bị tác động. */
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    /** @return Tên đối tượng bị tác động. */
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    /** @return Mô tả chi tiết hành động. */
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** @return Kết quả thực thi (SUCCESS/FAILURE). */
    public AuditResult getResult() { return result; }
    public void setResult(AuditResult result) { this.result = result; }

    /** @return Địa chỉ IP. */
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    /** @return User-Agent. */
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
