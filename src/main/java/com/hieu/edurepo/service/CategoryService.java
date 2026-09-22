package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Category;
import org.springframework.lang.NonNull;

import java.util.List;

/**
 * Giao diện dịch vụ quản lý danh mục phân loại học liệu (Category Service).
 */
public interface CategoryService {

    /**
     * Lấy danh sách tất cả các danh mục trong hệ thống (bao gồm cả danh mục tạm ẩn).
     * @return Danh sách toàn bộ danh mục
     */
    List<Category> findAll();

    /**
     * Lấy danh sách các danh mục đang hoạt động (active = true) sắp xếp theo thứ tự hiển thị ưu tiên.
     * @return Danh sách danh mục đang kích hoạt
     */
    List<Category> findActive();

    /**
     * Tìm kiếm thông tin chi tiết của danh mục theo mã định danh ID.
     * @param id Mã danh mục
     * @return Đối tượng Category tương ứng
     */
    Category findById(@NonNull Long id);

    /**
     * Thêm mới hoặc cập nhật thông tin danh mục học liệu.
     * @param category Thực thể danh mục cần lưu
     * @return Danh mục sau khi lưu vào CSDL
     */
    Category save(@NonNull Category category);

    /**
     * Xóa danh mục khỏi hệ thống theo mã ID.
     * @param id Mã danh mục cần xóa
     */
    void deleteById(@NonNull Long id);
}
