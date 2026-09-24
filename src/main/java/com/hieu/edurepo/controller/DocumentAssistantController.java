package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.ChatFeedbackRequest;
import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.security.DocumentAssistantRateLimiter;
import com.hieu.edurepo.service.ChatMessageFeedbackService;
import com.hieu.edurepo.service.DocumentAssistantService;
import com.hieu.edurepo.service.OcrService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Controller REST API phục vụ Trợ lý tra cứu học liệu tự động (EduBot / Document Assistant).
 * 
 * Vai trò & Chức năng chính:
 * 1. Tiếp nhận tin nhắn tra cứu và bối cảnh hội thoại từ khung chat người dùng qua HTTP GET.
 * 2. Tiếp nhận ảnh chụp màn hình qua HTTP POST /vision, xác thực an toàn và trích xuất chữ qua OCR.
 * 3. Thực hiện Rate Limiting (Sliding Window) dựa trên địa chỉ IP client để phòng chống tấn công DoS.
 * 4. Chuẩn hóa độ dài câu hỏi (cắt ngắn tối đa 2000 ký tự) chống Mega-Prompt DoS.
 * 5. Điều hướng tới `DocumentAssistantService` để xử lý RAG / Tool Calling và trả về phản hồi JSON chuẩn hóa.
 */
@RestController
@RequestMapping("/api/document-assistant")
public class DocumentAssistantController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentAssistantController.class);

    /** Thông báo lỗi mặc định khi hệ thống gặp sự cố truy xuất dữ liệu */
    private static final String ERROR_MESSAGE = "Hiện chưa thể tải danh sách tài liệu. Bạn vui lòng thử lại sau.";

    /** Giới hạn độ dài tối đa của câu hỏi đầu vào (ký tự) nhằm ngăn chặn tấn công Mega-Prompt DoS */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    /** Dung lượng tối đa của ảnh tải lên (5MB) */
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    /** Dịch vụ nghiệp vụ chính cho Trợ lý học liệu AI */
    private final DocumentAssistantService assistantService;

    /** Thành phần kiểm soát tần suất truy vấn text theo IP người dùng */
    private final DocumentAssistantRateLimiter rateLimiter;

    /** Thành phần kiểm soát tần suất gửi ảnh Vision (5 ảnh/phút) để chống nghẽn CPU */
    private final DocumentAssistantRateLimiter visionRateLimiter;

    /** Dịch vụ nhận dạng quang học OCR */
    private final OcrService ocrService;

    /** Dịch vụ lưu trữ phản hồi đánh giá câu trả lời (Thumbs Up / Down) */
    private final ChatMessageFeedbackService feedbackService;

    /**
     * Constructor phục vụ khởi tạo thủ công hoặc kiểm thử đơn vị.
     */
    public DocumentAssistantController(DocumentAssistantService assistantService) {
        this(assistantService, new DocumentAssistantRateLimiter(20), null, null);
    }

    /**
     * Constructor phục vụ kiểm thử đơn vị với Rate Limiter tùy chỉnh.
     */
    public DocumentAssistantController(DocumentAssistantService assistantService,
                                       DocumentAssistantRateLimiter rateLimiter) {
        this(assistantService, rateLimiter, null, null);
    }

    /**
     * Constructor phục vụ kiểm thử đơn vị với OCR Service tùy chỉnh.
     */
    public DocumentAssistantController(DocumentAssistantService assistantService,
                                       DocumentAssistantRateLimiter rateLimiter,
                                       OcrService ocrService) {
        this(assistantService, rateLimiter, ocrService, null);
    }

    /**
     * Constructor phục vụ Spring Dependency Injection chính.
     */
    @Autowired
    public DocumentAssistantController(DocumentAssistantService assistantService,
                                       DocumentAssistantRateLimiter rateLimiter,
                                       @Autowired(required = false) OcrService ocrService,
                                       @Autowired(required = false) ChatMessageFeedbackService feedbackService) {
        this.assistantService = assistantService;
        this.rateLimiter = rateLimiter != null ? rateLimiter : new DocumentAssistantRateLimiter(20);
        this.visionRateLimiter = new DocumentAssistantRateLimiter(5);
        this.ocrService = ocrService;
        this.feedbackService = feedbackService;
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
            @RequestParam(required = false) Long scopedDocumentId,
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
        return search(safeMessage, context, scopedDocumentId);
    }

    /**
     * Endpoint HTTP GET /stream truyền phát phản hồi trực tiếp tới người dùng qua Server-Sent Events (SSE).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRequest(
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
            @RequestParam(required = false) Long scopedDocumentId,
            @RequestParam(required = false) Long sessionId,
            org.springframework.security.core.Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        SseEmitter emitter = new SseEmitter(90_000L); // Timeout 90 giây

        String clientIp = resolveClientIp(request);
        if (!rateLimiter.tryAcquire(clientIp)) {
            long retryAfter = rateLimiter.getRetryAfterSeconds(clientIp);
            if (response != null) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
            }
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                        "type", "RATE_LIMITED",
                        "message", "Bạn đang gửi câu hỏi quá nhanh. Vui lòng đợi " + retryAfter + " giây rồi thử lại nhé."
                )));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        String safeMessage = message != null && message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH)
                : message;

        DocumentAssistantContext context = new DocumentAssistantContext(contextKeyword, contextTopic,
                contextAuthor, contextLanguageCode, contextYear, contextSortMode, contextPage,
                contextAnchorAuthor, contextAnchorTopic);

        emitter.onCompletion(() -> LOGGER.debug("SSE stream completed for client: {}", clientIp));
        emitter.onTimeout(() -> {
            LOGGER.debug("SSE stream timeout for client: {}", clientIp);
            emitter.complete();
        });
        emitter.onError(e -> LOGGER.debug("SSE stream error for client {}: {}", clientIp, e.getMessage()));

        Long userId = (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal p) ? p.getId() : null;
        assistantService.streamResponse(safeMessage, context, scopedDocumentId, sessionId, userId, emitter);
        return emitter;
    }

    /**
     * Endpoint HTTP GET /api/document-assistant/chunks/{chunkId}
     * Truy xuất chính xác nội dung chunk và số trang tương ứng khi người dùng click/hover vào citation [1], [2], [4]...
     */
    @GetMapping("/chunks/{chunkId}")
    public ResponseEntity<?> getChunkDetail(
            @PathVariable Long chunkId,
            @RequestParam(required = false) Integer citationIndex,
            @RequestParam(required = false) Long documentId) {
        return assistantService.getCitationDetail(chunkId, citationIndex, documentId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", "NOT_FOUND",
                        "message", "Không tìm thấy đoạn trích dẫn (chunkId=" + chunkId + ")."
                )));
    }

    /**
     * Endpoint HTTP GET /api/document-assistant/citation
     * Alias endpoint hỗ trợ truy vấn citation theo query params (?chunkId=...&citationIndex=...)
     */
    @GetMapping("/citation")
    public ResponseEntity<?> getCitationByParam(
            @RequestParam(required = false) Long chunkId,
            @RequestParam(required = false) Integer citationIndex,
            @RequestParam(required = false) Long documentId) {
        if (chunkId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", "Thiếu chunkId"));
        }
        return getChunkDetail(chunkId, citationIndex, documentId);
    }

    /**
     * Endpoint HTTP POST /feedback tiếp nhận đánh giá phản hồi (Thumbs Up / Down) từ người dùng.
     */
    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(
            @Valid @RequestBody ChatFeedbackRequest feedbackRequest,
            org.springframework.security.core.Authentication authentication,
            HttpServletRequest request) {

        if (feedbackService == null) {
            return ResponseEntity.status(503).body(Map.of("error", "Dịch vụ đánh giá phản hồi chưa sẵn sàng."));
        }

        String clientIp = resolveClientIp(request);
        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            userId = principal.getId();
        }

        var saved = feedbackService.recordFeedback(feedbackRequest, userId, clientIp);
        java.util.Map<String, Object> responseBody = new java.util.HashMap<>();
        responseBody.put("status", "SUCCESS");
        responseBody.put("message", "Cảm ơn bạn đã đánh giá câu trả lời!");
        responseBody.put("feedbackId", (saved != null && saved.getId() != null) ? saved.getId() : 0L);
        responseBody.put("rating", (saved != null && saved.getRating() != null) ? saved.getRating().name() : "NONE");
        return ResponseEntity.ok(responseBody);
    }


    /**
     * Endpoint HTTP POST /vision tiếp nhận ảnh chụp màn hình (PrtScn / Clipboard) và câu hỏi kèm theo.
     * 
     * Quy trình xử lý:
     * 1. Rate Limiting chuyên biệt cho Vision (5 requests/phút/IP).
     * 2. Kiểm tra dung lượng (tối đa 5MB) và Magic Bytes (PNG, JPEG, WebP).
     * 3. Gọi OcrService để trích xuất chữ trong ảnh.
     * 4. Tổng hợp văn bản từ ảnh và câu hỏi của người dùng đưa vào luồng RAG tra cứu học liệu.
     */
    @PostMapping(value = "/vision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentAssistantResponse searchWithImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "message", defaultValue = "") String message,
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

        // 1. Kiểm tra Rate Limiting chuyên biệt cho xử lý ảnh
        String clientIp = resolveClientIp(request);
        if (!visionRateLimiter.tryAcquire(clientIp)) {
            long retryAfter = visionRateLimiter.getRetryAfterSeconds(clientIp);
            if (response != null) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
            }
            return new DocumentAssistantResponse(
                    "RATE_LIMITED",
                    "Bạn đang gửi yêu cầu nhận diện ảnh quá nhanh. Vui lòng đợi " + retryAfter + " giây rồi thử lại nhé.",
                    message,
                    Map.of("retryAfterSeconds", String.valueOf(retryAfter)),
                    List.of(),
                    List.of("Thử lại sau ít phút"),
                    false,
                    "",
                    new DocumentAssistantContext(contextKeyword, contextTopic, contextAuthor, contextLanguageCode, contextYear, contextSortMode, contextPage, contextAnchorAuthor, contextAnchorTopic),
                    List.of()
            );
        }

        // 2. Validate tệp rỗng hoặc kích thước vượt quá 5MB
        if (image == null || image.isEmpty()) {
            if (response != null) response.setStatus(400);
            return DocumentAssistantResponse.message("BAD_REQUEST", "Tệp hình ảnh không được để trống.");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            if (response != null) response.setStatus(400);
            return DocumentAssistantResponse.message("PAYLOAD_TOO_LARGE", "Kích thước ảnh vượt quá giới hạn cho phép (tối đa 5MB).");
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (Exception e) {
            LOGGER.warn("Failed to read image bytes: {}", e.getMessage());
            if (response != null) response.setStatus(400);
            return DocumentAssistantResponse.message("BAD_REQUEST", "Không thể đọc dữ liệu tệp ảnh tải lên.");
        }

        // 3. Kiểm tra chữ ký byte đầu (Magic Bytes) chống đổi đuôi file độc hại
        if (!isSupportedImage(bytes)) {
            if (response != null) response.setStatus(400);
            return DocumentAssistantResponse.message("UNSUPPORTED_MEDIA_TYPE", "Định dạng tệp không hợp lệ. Hệ thống chỉ hỗ trợ ảnh PNG, JPEG hoặc WebP.");
        }

        // 4. Trích xuất văn bản từ ảnh thông qua OcrService
        String ocrText = "";
        boolean ocrEngineReady = ocrService != null && ocrService.isAvailable();
        if (ocrEngineReady) {
            try {
                ocrText = ocrService.extractText(bytes);
            } catch (Exception e) {
                LOGGER.warn("OCR extraction failed for vision request: {}", e.getMessage());
            }
        }

        String safeMessage = message != null ? message.trim() : "";
        String safeOcrText = ocrText != null ? ocrText.trim() : "";

        // Nếu máy chủ chưa cài hoặc chưa bật Tesseract OCR
        if (!ocrEngineReady && safeOcrText.isEmpty()) {
            if (safeMessage.length() < 5) {
                return DocumentAssistantResponse.message("OCR_UNAVAILABLE",
                        "Tính năng quét chữ từ ảnh chụp yêu cầu Tesseract OCR trên máy chủ (hiện chưa được phát hiện hoặc chưa cài đặt). "
                                + "Bạn hãy gõ cụ thể nội dung câu hỏi (ví dụ: 'A Philosophy of Data Structures là gì?') vào khung chat để EduBot giải đáp và tìm tài liệu ngay nhé!");
            }
        }

        // Nếu cả ảnh không đọc được chữ và người dùng không nhập câu hỏi kèm theo
        if (safeOcrText.isEmpty() && safeMessage.isEmpty()) {
            return DocumentAssistantResponse.message("NO_TEXT_FOUND",
                    "Chatbot không nhận diện được chữ trong ảnh chụp. Bạn vui lòng chụp rõ nét phần câu hỏi/bài tập cần tra cứu, hoặc gõ kèm câu hỏi vào khung chat nhé!");
        }

        // Nếu không đọc được chữ từ ảnh và câu hỏi nhập kèm quá ngắn/chung chung (vd: "là gì")
        if (safeOcrText.isEmpty() && safeMessage.length() < 5) {
            return DocumentAssistantResponse.message("INSUFFICIENT_INPUT",
                    "Chatbot chưa nhận diện được chữ trong ảnh và câu hỏi \"" + safeMessage + "\" chưa đủ thông tin để tra cứu. "
                            + "Bạn hãy nhập cụ thể tên chủ đề hoặc khái niệm (ví dụ: 'Cấu trúc dữ liệu là gì?') nhé!");
        }

        // 5. Đóng gói câu hỏi tổng hợp
        StringBuilder combinedPrompt = new StringBuilder();
        if (!safeMessage.isEmpty()) {
            combinedPrompt.append(safeMessage);
        }
        if (!safeOcrText.isEmpty()) {
            if (combinedPrompt.length() > 0) {
                combinedPrompt.append("\n\n");
            }
            combinedPrompt.append("[Nội dung trích xuất từ ảnh chụp]:\n").append(safeOcrText);
        }

        String finalPrompt = combinedPrompt.toString();
        if (finalPrompt.length() > MAX_MESSAGE_LENGTH) {
            finalPrompt = finalPrompt.substring(0, MAX_MESSAGE_LENGTH);
        }

        DocumentAssistantContext context = new DocumentAssistantContext(contextKeyword, contextTopic,
                contextAuthor, contextLanguageCode, contextYear, contextSortMode, contextPage,
                contextAnchorAuthor, contextAnchorTopic);
        return context.hasValues() ? search(finalPrompt, context) : search(finalPrompt);
    }

    /**
     * Xác thực Magic Bytes của các định dạng ảnh phổ biến (PNG, JPEG, WebP).
     */
    private boolean isSupportedImage(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        // PNG: 89 50 4E 47
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return true;
        }
        // JPEG: FF D8 FF
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return true;
        }
        // WebP: RIFF ... WEBP
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return true;
        }
        return false;
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
        return search(message, null, null);
    }

    public DocumentAssistantResponse search(String message, DocumentAssistantContext context) {
        return search(message, context, null);
    }

    /**
     * Chuyển tiếp câu hỏi sang `assistantService` và bắt các ngoại lệ runtime để bảo vệ an toàn hệ thống.
     */
    private DocumentAssistantResponse search(String message, DocumentAssistantContext context, Long scopedDocumentId) {
        try {
            if (scopedDocumentId != null) {
                return assistantService.respond(message, context, scopedDocumentId);
            }
            return (context != null && context.hasValues())
                    ? assistantService.respond(message, context)
                    : assistantService.respond(message);
        } catch (RuntimeException exception) {
            // Không ghi nội dung chi tiết câu hỏi người dùng vào log nhằm bảo mật thông tin cá nhân
            LOGGER.error("Document assistant search failed ({})", exception.getClass().getSimpleName());
            return DocumentAssistantResponse.message("ERROR", ERROR_MESSAGE);
        }
    }
}


