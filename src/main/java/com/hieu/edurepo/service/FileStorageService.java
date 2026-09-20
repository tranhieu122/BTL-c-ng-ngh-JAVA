package com.hieu.edurepo.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface quản lý lưu trữ và truy xuất file tài liệu trên hệ thống.
 *
 * <p>Cung cấp các thao tác CRUD cơ bản cho file vật lý:</p>
 * <ul>
 *   <li>{@link #store(MultipartFile)} – Lưu file tải lên vào thư mục storage, trả về tên file duy nhất.</li>
 *   <li>{@link #load(String)} – Tải file theo tên đã lưu để phục vụ download/view.</li>
 *   <li>{@link #checksum(String)} – Tính SHA-256 của file để phát hiện thay đổi/tham chiếu.</li>
 *   <li>{@link #delete(String)} – Xóa file vật lý khỏi storage (gọi khi tài liệu bị xóa).</li>
 * </ul>
 *
 * <p>Implementation: {@code FileStorageServiceImpl} – lưu file vào thư mục cấu hình
 * ({@code app.storage.upload-dir}) với tên UUID ngẫu nhiên để tránh xung đột.</p>
 */
public interface FileStorageService {

    /**
     * Lưu file tải lên vào thư mục storage.
     * Tên file gốc được thay bằng UUID ngẫu nhiên + phần mở rộng để tránh xung đột
     * và ngăn path traversal.
     *
     * @param file File tải lên từ browser.
     * @return Tên file đã lưu (UUID + extension), dùng để tải lại sau này.
     * @throws com.hieu.edurepo.exception.FileStorageException Nếu lỗi lưu file.
     */
    String store(MultipartFile file);

    /**
     * Tải file từ storage theo tên file đã lưu.
     * Dùng để phục vụ download hoặc hiển thị PDF trực tiếp trên trình duyệt.
     *
     * @param storedFileName Tên file đã lưu (UUID + extension, không có đường dẫn).
     * @return {@link Resource} Spring để stream nội dung file.
     * @throws com.hieu.edurepo.exception.FileStorageException Nếu file không tồn tại hoặc không đọc được.
     */
    Resource load(String storedFileName);

    /**
     * Tính checksum SHA-256 (hex) của file đã lưu.
     * Dùng để phát hiện file bị thay đổi hoặc so sánh tính toàn vẹn.
     *
     * @param storedFileName Tên file đã lưu.
     * @return Chuỗi hex SHA-256 của nội dung file.
     * @throws com.hieu.edurepo.exception.FileStorageException Nếu không đọc được file.
     */
    String checksum(String storedFileName);

    /**
     * Xóa file vật lý khỏi storage.
     * Được gọi khi tài liệu bị xóa hoặc khi upload file mới thay thế file cũ.
     *
     * @param storedFileName Tên file cần xóa.
     */
    void delete(String storedFileName);
}
