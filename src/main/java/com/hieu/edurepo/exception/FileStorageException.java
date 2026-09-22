package com.hieu.edurepo.exception;

/**
 * Ngoại lệ xảy ra trong quá trình thao tác với hệ thống lưu trữ tệp tin vật lý
 * (ví dụ: tạo thư mục, đọc/ghi tệp, tệp bị khóa, đường dẫn không hợp lệ).
 */
public class FileStorageException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ với thông điệp lỗi cụ thể.
     * @param message Thông điệp mô tả nguyên nhân lỗi lưu trữ
     */
    public FileStorageException(String message) {
        super(message);
    }

    /**
     * Khởi tạo ngoại lệ với thông điệp và nguyên nhân gốc (Throwable).
     * @param message Thông điệp lỗi
     * @param cause Nguyên nhân ngoại lệ gốc (ví dụ: IOException)
     */
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
