package com.hieu.edurepo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity đại diện cho người dùng hệ thống EduRepo.
 *
 * <p>Lưu trữ thông tin xác thực (username/email/password), trạng thái tài khoản
 * (enabled/locked), thông tin cá nhân (họ tên, số điện thoại, tiểu sử, avatar)
 * và danh sách vai trò (ROLE_ADMIN, ROLE_LIBRARIAN, ROLE_SUBMITTER, ROLE_USER).</p>
 *
 * <p>Cơ chế khóa tài khoản: sau {@code N} lần đăng nhập thất bại liên tiếp,
 * trường {@code loginLockedUntil} sẽ được đặt để tạm khóa tài khoản theo thời gian.</p>
 *
 * <p>Bảng CSDL: {@code users}</p>
 */
@Entity
@Table(name = "users")
public class User {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên đăng nhập duy nhất trong hệ thống.
     * Dùng để xác thực và hiển thị trên UI.
     */
    @Column(nullable = false, unique = true)
    private String username;

    /** Họ và tên đầy đủ của người dùng. */
    private String fullName;

    /** Địa chỉ email duy nhất, dùng cho xác thực OTP và liên lạc. */
    @Column(nullable = false, unique = true)
    private String email;

    /** Mật khẩu đã được mã hóa bằng BCrypt. */
    @Column(nullable = false)
    private String password;

    /**
     * Trạng thái kích hoạt tài khoản.
     * {@code false} khi tài khoản bị vô hiệu hóa bởi admin.
     */
    private boolean enabled = true;

    /**
     * Số lần đăng nhập thất bại liên tiếp.
     * Dùng bởi {@code LoginAttemptService} để kiểm soát brute-force.
     */
    @Column(nullable = false)
    private int failedLoginAttempts;

    /**
     * Thời điểm tài khoản bị khóa đến (inclusive).
     * Nếu null hoặc trong quá khứ, tài khoản không bị khóa.
     */
    private LocalDateTime loginLockedUntil;

    /** Số điện thoại liên hệ (tùy chọn). */
    @Column(length = 30)
    private String phoneNumber;

    /** Đơn vị công tác / trường / khoa (tùy chọn). */
    @Column(length = 150)
    private String affiliation;

    /** Tiểu sử ngắn của người dùng (tùy chọn). */
    @Column(length = 1000)
    private String bio;

    /**
     * Khóa lưu trữ ảnh đại diện (key trên S3 hoặc đường dẫn tương đối).
     * Null nếu người dùng chưa đặt ảnh đại diện.
     */
    private String avatarKey;

    /**
     * Khóa bootstrap một lần dùng để kích hoạt tài khoản hoặc đặt lại mật khẩu.
     * Xóa sau khi sử dụng.
     */
    @Column(length = 64)
    private String bootstrapKey;

    /**
     * Thời điểm tài khoản bị xóa mềm (soft delete).
     * Null nếu tài khoản còn hoạt động.
     */
    private java.time.LocalDateTime deletedAt;

    // ---- Getters/Setters cho các trường profile ----

    /** @return Số điện thoại. */
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String value) { phoneNumber = value; }

    /** @return Đơn vị công tác. */
    public String getAffiliation() { return affiliation; }
    public void setAffiliation(String value) { affiliation = value; }

    /** @return Tiểu sử người dùng. */
    public String getBio() { return bio; }
    public void setBio(String value) { bio = value; }

    /** @return Khóa ảnh đại diện. */
    public String getAvatarKey() { return avatarKey; }

    /** @return Khóa bootstrap. */
    public String getBootstrapKey() { return bootstrapKey; }
    public void setBootstrapKey(String value) { bootstrapKey = value; }
    public void setAvatarKey(String value) { avatarKey = value; }

    /** @return Thời điểm xóa mềm. */
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.LocalDateTime value) { deletedAt = value; }

    /**
     * Thời điểm người dùng gửi yêu cầu đặt lại mật khẩu gần nhất.
     * Dùng để giới hạn tần suất gửi email reset.
     */
    private java.time.LocalDateTime passwordResetRequestedAt;

    /** @return Thời điểm yêu cầu đặt lại mật khẩu. */
    public java.time.LocalDateTime getPasswordResetRequestedAt() { return passwordResetRequestedAt; }
    public void setPasswordResetRequestedAt(java.time.LocalDateTime value) { passwordResetRequestedAt = value; }

    /**
     * Tập hợp vai trò của người dùng.
     * Eager-loaded để Spring Security có thể kiểm tra quyền ngay khi load principal.
     * Bảng trung gian: {@code user_roles}.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /** Constructor mặc định yêu cầu bởi JPA. */
    public User() {
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID người dùng. */
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** @return Tên đăng nhập. */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /** @return Họ và tên đầy đủ. */
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** @return Địa chỉ email. */
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /** @return Mật khẩu đã mã hóa. */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /** @return {@code true} nếu tài khoản đang được kích hoạt. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return Số lần đăng nhập thất bại liên tiếp. */
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    /** @return Thời điểm tài khoản bị khóa đến. */
    public LocalDateTime getLoginLockedUntil() {
        return loginLockedUntil;
    }

    public void setLoginLockedUntil(LocalDateTime loginLockedUntil) {
        this.loginLockedUntil = loginLockedUntil;
    }

    /**
     * Kiểm tra xem tài khoản có đang bị khóa tại thời điểm cho trước hay không.
     *
     * @param time Thời điểm cần kiểm tra.
     * @return {@code true} nếu tài khoản còn trong thời gian bị khóa.
     */
    public boolean isLoginLockedAt(LocalDateTime time) {
        return loginLockedUntil != null && loginLockedUntil.isAfter(time);
    }

    /** @return Tập hợp vai trò của người dùng. */
    public Set<Role> getRoles() {
        return roles;
    }

    /**
     * Gán danh sách vai trò mới.
     * Luôn tạo bản sao mới của Set để tránh tham chiếu ngoài thay đổi trực tiếp.
     *
     * @param roles Danh sách vai trò; nếu null thì gán Set rỗng.
     */
    public void setRoles(Set<Role> roles) {
        this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
    }
}
