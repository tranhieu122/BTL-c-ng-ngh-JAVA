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

/**
 * Bộ bắt và xử lý ngoại lệ tập trung toàn cục (Global Exception Handler) cho toàn bộ ứng dụng EduRepo.
 * <p>
 * Bắt các ngoại lệ phổ biến và điều hướng về trang lỗi tiếng Việt thân thiện tương ứng (400, 403, 404, 413, 500).
 * </p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Xử lý lỗi khi người dùng truy cập URL không tồn tại trong hệ thống (HTTP 404).
     * @param model Đối tượng chứa dữ liệu truyền sang giao diện
     * @return Tên view giao diện lỗi 404
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String missingRoute(Model model) {
        model.addAttribute("message", "Không tìm thấy trang được yêu cầu.");
        return "error/404";
    }

    /**
     * Xử lý lỗi khi không tìm thấy bản ghi dữ liệu cụ thể trong CSDL (HTTP 404).
     * @param exception Ngoại lệ ResourceNotFoundException ném ra từ Service
     * @param model Đối tượng dữ liệu view
     * @return Tên view giao diện lỗi 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(ResourceNotFoundException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/404";
    }

    /**
     * Xử lý các lỗi yêu cầu không hợp lệ hoặc lỗi lưu trữ tệp tin (HTTP 400 Bad Request).
     * @param exception Ngoại lệ nghiệp vụ vi phạm
     * @param model Đối tượng dữ liệu view
     * @return Tên view giao diện lỗi 400
     */
    @ExceptionHandler({InvalidStatusException.class, FileStorageException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(RuntimeException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/400";
    }

    /**
     * Xử lý lỗi truy cập trái phép khi người dùng không đủ quyền hạn (HTTP 403 Forbidden).
     * @param exception Ngoại lệ bảo mật từ Spring Security
     * @param model Đối tượng dữ liệu view
     * @return Tên view giao diện thông báo từ chối truy cập 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(AccessDeniedException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error/403";
    }

    /**
     * Xử lý lỗi khi người dùng tải lên tệp tin vượt quá dung lượng tối đa 200MB (HTTP 413 Payload Too Large).
     * @param model Đối tượng dữ liệu view
     * @return Tên view giao diện cảnh báo vượt dung lượng 413
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String uploadTooLarge(Model model) {
        model.addAttribute("message", "Tệp tải lên vượt quá dung lượng tối đa 200 MB");
        return "error/413";
    }

    /**
     * Xử lý tất cả các ngoại lệ không mong muốn khác trong hệ thống (HTTP 500 Internal Server Error).
     * @param exception Ngoại lệ không lường trước
     * @param model Đối tượng dữ liệu view
     * @return Tên view giao diện sự cố máy chủ 500
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unexpected(Exception exception, Model model) {
        LOGGER.error("Unexpected request processing error", exception);
        model.addAttribute("message", "Đã xảy ra lỗi. Vui lòng thử lại.");
        return "error/500";
    }
}
