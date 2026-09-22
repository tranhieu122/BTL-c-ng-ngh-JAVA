package com.hieu.edurepo.exception;

/**
 * Ngoại lệ ném ra khi có hành vi chuyển đổi trạng thái tài liệu sai quy trình
 * (ví dụ: tài liệu đang ở trạng thái REJECTED nhưng lại gọi trực tiếp PUBLISHED).
 */
public class InvalidStatusException extends RuntimeException {

    /**
     * Khởi tạo ngoại lệ trạng thái không hợp lệ.
     * @param message Thông điệp mô tả chi tiết trạng thái vi phạm
     */
    public InvalidStatusException(String message) {
        super(message);
    }
}
