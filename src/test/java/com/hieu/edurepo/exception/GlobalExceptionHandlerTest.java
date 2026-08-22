package com.hieu.edurepo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
