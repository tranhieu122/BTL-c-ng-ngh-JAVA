package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.service.DocumentAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document-assistant")
public class DocumentAssistantController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentAssistantController.class);
    private static final String ERROR_MESSAGE = "Hiện chưa thể tải danh sách tài liệu. Bạn vui lòng thử lại sau.";

    private final DocumentAssistantService assistantService;

    public DocumentAssistantController(DocumentAssistantService assistantService) {
        this.assistantService = assistantService;
    }

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
            @RequestParam(defaultValue = "") String contextAnchorTopic) {
        DocumentAssistantContext context = new DocumentAssistantContext(contextKeyword, contextTopic,
                contextAuthor, contextLanguageCode, contextYear, contextSortMode, contextPage,
                contextAnchorAuthor, contextAnchorTopic);
        return context.hasValues() ? search(message, context) : search(message);
    }

    public DocumentAssistantResponse search(String message) {
        return search(message, null);
    }

    private DocumentAssistantResponse search(String message, DocumentAssistantContext context) {
        try {
            return context == null ? assistantService.respond(message) : assistantService.respond(message, context);
        } catch (RuntimeException exception) {
            // Không ghi nội dung người dùng, thông báo lỗi hoặc chi tiết truy vấn vào log.
            LOGGER.error("Document assistant search failed ({})", exception.getClass().getSimpleName());
            return DocumentAssistantResponse.message("ERROR", ERROR_MESSAGE);
        }
    }
}
