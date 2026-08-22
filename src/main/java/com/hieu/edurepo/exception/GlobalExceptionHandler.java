package com.hieu.edurepo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(ResourceNotFoundException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/404";
    }

    @ExceptionHandler({InvalidStatusException.class, FileStorageException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(RuntimeException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/400";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(AccessDeniedException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/403";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String uploadTooLarge(Model model) {
        model.addAttribute("message", "Tệp tải lên vượt quá dung lượng tối đa 200 MB");
        return "error/413";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unexpected(Exception exception, Model model) {
        LOGGER.error("Unexpected request processing error", exception);
        model.addAttribute("message", "Đã xảy ra lỗi. Vui lòng thử lại.");
        return "error/500";
    }
}
