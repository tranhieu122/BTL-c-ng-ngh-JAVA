package com.hieu.edurepo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kiểm thử bộ xử lý ngoại lệ toàn cục (Global Exception Handler Test).
 * Xác minh việc ánh xạ ngoại lệ nghiệp vụ sang mã HTTP và trang giao diện lỗi tiếng Việt tương ứng (400, 413).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badRequestAndOversizedUploadUseMatchingErrorPages() {
        ConcurrentModel badRequestModel = new ConcurrentModel();
        ConcurrentModel uploadModel = new ConcurrentModel();

        assertEquals("error/400",
                handler.badRequest(new InvalidStatusException("Sai trạng thái"), badRequestModel));
        assertEquals("Sai trạng thái", badRequestModel.getAttribute("message"));
        assertEquals("error/413", handler.uploadTooLarge(uploadModel));
        assertEquals("Tệp tải lên vượt quá dung lượng tối đa 200 MB",
                uploadModel.getAttribute("message"));
    }
}
