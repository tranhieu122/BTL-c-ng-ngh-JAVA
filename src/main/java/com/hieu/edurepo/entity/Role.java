package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity đại diện cho vai trò (role) phân quyền trong hệ thống EduRepo.
 *
 * <p>Hệ thống sử dụng mô hình RBAC (Role-Based Access Control). Mỗi vai trò
 * được ánh xạ với một giá trị enum {@link RoleName}, ví dụ:</p>
 * <ul>
 *   <li>{@code ROLE_ADMIN} – Quản trị viên toàn quyền.</li>
 *   <li>{@code ROLE_LIBRARIAN} – Thủ thư/kiểm duyệt viên.</li>
 *   <li>{@code ROLE_SUBMITTER} – Người nộp tài liệu.</li>
 *   <li>{@code ROLE_USER} – Người dùng xem/tải tài liệu.</li>
 * </ul>
 *
 * <p>Bảng CSDL: {@code roles}</p>
 */
@Entity
@Table(name = "roles")
public class Role {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên vai trò dưới dạng enum, lưu trữ dạng chuỗi trong CSDL.
     * Unique để đảm bảo mỗi vai trò chỉ tồn tại một bản ghi.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;

    /** Constructor mặc định yêu cầu bởi JPA. */
    public Role() {
    }

    /**
     * Constructor tiện ích để tạo role với tên cụ thể.
     *
     * @param name Tên vai trò.
     */
    public Role(RoleName name) {
        this.name = name;
    }

    /** @return ID vai trò. */
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** @return Tên vai trò (enum). */
    public RoleName getName() {
        return name;
    }

    public void setName(RoleName name) {
        this.name = name;
    }
}
