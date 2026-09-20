package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.security.DocumentAssistantRateLimiter;
import com.hieu.edurepo.service.DocumentAssistantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controller REST API phục vụ Trợ lý tra cứu học liệu tự động (EduBot / Document Assistant).
 * 
 * Vai trò & Chức năng chính:
 * 1. Tiếp nhận tin nhắn tra cứu và bối cảnh hội thoại từ khung chat người dùng qua HTTP GET.
 * 2. Thực hiện Rate Limiting (Sliding Window) dựa trên địa chỉ IP client để phòng chống tấn công DoS và lạm dụng API OpenAI.
 * 3. Chuẩn hóa độ dài câu hỏi (cắt ngắn tối đa 2000 ký tự) chống Mega-Prompt DoS.
 * 4. Điều hướng tới `DocumentAssistantService` để xử lý RAG / Tool Calling và trả về phản hồi JSON chuẩn hóa.
 */
@RestController
@RequestMapping("/api/document-assistant")
public class DocumentAssistantController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentAssistantController.class);

    /** Thông báo lỗi mặc định khi hệ thống gặp sự cố truy xuất dữ liệu */
    private static final String ERROR_MESSAGE = "Hiện chưa thể tải danh sách tài liệu. Bạn vui lòng thử lại sau.";

    /** Giới hạn độ dài tối đa của câu hỏi đầu vào (ký tự) nhằm ngăn chặn tấn công Mega-Prompt DoS */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    /** Dịch vụ nghiệp vụ chính cho Trợ lý học liệu AI */
    private final DocumentAssistantService assistantService;

    /** Thành phần kiểm soát tần suất truy vấn theo IP người dùng */
    private final DocumentAssistantRateLimiter rateLimiter;

    /**
     * Constructor phục vụ khởi tạo thủ công hoặc kiểm thử đơn vị.
     * Mặc định khởi tạo Rate Limiter 20 requests/phút.
     * 
     * @param assistantService Dịch vụ Trợ lý học liệu
     */
    public DocumentAssistantController(DocumentAssistantService assistantService) {
        this(assistantService, new DocumentAssistantRateLimiter(20));
    }

    /**
     * Constructor phục vụ Spring Dependency Injection chính.
     * 
     * @param assistantService Dịch vụ Trợ lý học liệu
     * @param rateLimiter Thành phần Rate Limiter kiểm soát tần suất gọi API
     */
    @Autowired
    public DocumentAssistantController(DocumentAssistantService assistantService,
                                       DocumentAssistantRateLimiter rateLimiter) {
        this.assistantService = assistantService;
        this.rateLimiter = rateLimiter != null ? rateLimiter : new DocumentAssistantRateLimiter(20);
    }

    /**
     * Endpoint HTTP GET tiếp nhận câu hỏi hoặc yêu cầu tra cứu từ khung chat.
     * 
     * @param message Tin nhắn người dùng nhập vào khung chat.
     * @param contextKeyword Từ khóa đã lọc trước đó từ ngữ cảnh hội thoại.
     * @param contextTopic Chủ đề/môn học đã được neo trong ngữ cảnh.
     * @param contextAuthor Tác giả đang được tìm kiếm.
     * @param contextLanguageCode Ngôn ngữ ("vi" hoặc "en").
     * @param contextYear Năm xuất bản tài liệu.
     * @param contextSortMode Chế độ sắp xếp ("newest", "popular", "rating", "downloaded", "relevant").
     * @param contextPage Số trang hiện tại khi xem tiếp ("Xem thêm").
     * @param contextAnchorAuthor Tác giả neo khi hỏi các câu tiếp theo ("tài liệu cùng tác giả").
     * @param contextAnchorTopic Chủ đề neo khi hỏi các câu tiếp theo ("tài liệu cùng chủ đề").
     * @param request Yêu cầu HTTP Servlet để trích xuất địa chỉ IP client
     * @param response Phản hồi HTTP Servlet để thiết lập HTTP Status 429 và Header Retry-After
     * @return Đối tượng DocumentAssistantResponse chứa câu trả lời AI, danh sách tài liệu hoặc gợi ý.
     */
    @GetMapping
    public DocumentAssistantResponse searchRequest(
            @RequestParam(defaultValue = "") String message,
            @RequestParam(defaultValue = "") String contextKeyword,
            @RequestParam(defaultValue = "") String contextTopic,
            @RequestParam(defaultValue = "") String contextAuthor,
            @RequestParam(defaultValue = "") String contextLanguageCode,
            @RequestParam(required = false) Integer contextYear,
            @RequestParam(defaultValue = "") String contextSortMode,
            @RequestParam(defaultValue = "0") int contextPage,
            @RequestParam(defaultValue = "") String contextAnchorAuthor,
            @RequestParam(defaultValue = "") String contextAnchorTopic,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 1. Kiểm tra giới hạn tần suất gửi yêu cầu (Rate Limiting) dựa trên IP
        String clientIp = resolveClientIp(request);
        if (!rateLimiter.tryAcquire(clientIp)) {
            long retryAfter = rateLimiter.getRetryAfterSeconds(clientIp);
            if (response != null) {
                response.setStatus(429); // HTTP 429 Too Many Requests
                response.setHeader("Retry-After", String.valueOf(retryAfter));
            }
            return new DocumentAssistantResponse(
                    "RATE_LIMITED",
                    "Bạn đang gửi câu hỏi quá nhanh. Vui lòng đợi " + retryAfter + " giây rồi thử lại nhé.",
                    message,
                    Map.of("retryAfterSeconds", String.valueOf(retryAfter)),
                    List.of(),
                    List.of("Tài liệu mới nhất", "Thử lại sau ít phút"),
                    false,
                    "",
                    new DocumentAssistantContext(contextKeyword, contextTopic, contextAuthor, contextLanguageCode, contextYear, contextSortMode, contextPage, contextAnchorAuthor, contextAnchorTopic),
                    List.of()
            );
        }

        // 2. Chuẩn hóa & cắt ngắn câu hỏi nếu vượt quá hạn mức độ dài an toàn
        String safeMessage = message != null && message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH)
                : message;

        // 3. Đóng gói đối tượng bối cảnh hội thoại và chuyển sang tầng Service xử lý
        DocumentAssistantContext context = new DocumentAssistantContext(contextKeyword, contextTopic,
                contextAuthor, contextLanguageCode, contextYear, contextSortMode, contextPage,
                contextAnchorAuthor, contextAnchorTopic);
        return context.hasValues() ? search(safeMessage, context) : search(safeMessage);
    }

    /**
     * Bóc tách địa chỉ IP thực tế của client từ ServletRequest (xử lý qua Reverse Proxy / Load Balancer).
     * 
     * @param request Yêu cầu HTTP
     * @return Chuỗi IP client (ví dụ: "192.168.1.1" hoặc "127.0.0.1")
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int commaIdx = xff.indexOf(',');
            return commaIdx > 0 ? xff.substring(0, commaIdx).trim() : xff.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "127.0.0.1";
    }

    /**
     * Phương thức overload hỗ trợ gọi tìm kiếm không có bối cảnh hội thoại.
     */
    public DocumentAssistantResponse search(String message) {
        return search(message, null);
    }

    /**
     * Chuyển tiếp câu hỏi sang `assistantService` và bắt các ngoại lệ runtime để bảo vệ an toàn hệ thống.
     */
    private DocumentAssistantResponse search(String message, DocumentAssistantContext context) {
        try {
            return context == null ? assistantService.respond(message) : assistantService.respond(message, context);
        } catch (RuntimeException exception) {
            // Không ghi nội dung chi tiết câu hỏi người dùng vào log nhằm bảo mật thông tin cá nhân
            LOGGER.error("Document assistant search failed ({})", exception.getClass().getSimpleName());
            return DocumentAssistantResponse.message("ERROR", ERROR_MESSAGE);
        }
    }
}
