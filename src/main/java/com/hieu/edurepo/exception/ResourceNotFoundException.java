package com.hieu.edurepo.exception;

/**
 * Ngoại lệ ném ra khi không tìm thấy tài nguyên được yêu cầu trong cơ sở dữ liệu
 * (ví dụ: không tìm thấy Document, User, Category, Notification theo ID).
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ không tìm thấy tài nguyên.
     * @param message Thông điệp thông báo tài nguyên không tồn tại
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
