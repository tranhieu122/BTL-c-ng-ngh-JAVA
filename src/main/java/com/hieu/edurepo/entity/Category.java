package com.hieu.edurepo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entity đại diện cho danh mục (category) phân loại tài liệu trong hệ thống.
 *
 * <p>Mỗi tài liệu thuộc về một danh mục duy nhất. Danh mục giúp người dùng
 * lọc và tìm kiếm tài liệu theo chủ đề. Admin có thể bật/tắt danh mục
 * ({@code active}) mà không cần xóa.</p>
 *
 * <p>Bảng CSDL: {@code categories}</p>
 */
@Entity
@Table(name = "categories")
public class Category {

    /** Khóa chính, tự động tăng. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên danh mục, duy nhất trong toàn hệ thống.
     * Không được để trống và tối đa 150 ký tự.
     */
    @Column(nullable = false, unique = true)
    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 150, message = "Tên danh mục không được vượt quá 150 ký tự")
    private String name;

    /**
     * Mô tả ngắn về danh mục (tùy chọn, tối đa 255 ký tự).
     * Hiển thị trong trang quản lý danh mục của admin.
     */
    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;

    /**
     * Trạng thái hoạt động của danh mục.
     * Danh mục bị vô hiệu hóa ({@code false}) sẽ không hiển thị khi tạo tài liệu mới.
     */
    private boolean active = true;

    /** Constructor mặc định yêu cầu bởi JPA. */
    public Category() {
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    /** @return ID danh mục. */
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** @return Tên danh mục. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** @return Mô tả danh mục. */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** @return {@code true} nếu danh mục đang hoạt động. */
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
