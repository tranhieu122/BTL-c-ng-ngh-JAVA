package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.service.DocumentAssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử bộ điều hướng Trợ lý AI và hỏi đáp giáo trình (Document Assistant Controller Test).
 * Xác minh việc gọi API chat, truyền phát phản hồi streaming SSE và kiểm soát quyền sở hữu phiên hội thoại.
 */
class DocumentAssistantControllerTest {

    @Test
    void exposesJsonSearchEndpoint() throws Exception {
        DocumentAssistantService service = mock(DocumentAssistantService.class);
        when(service.respond("Java")).thenReturn(DocumentAssistantResponse.message("NO_RESULTS", "Không có kết quả"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DocumentAssistantController(service)).build();

        mvc.perform(get("/api/document-assistant").param("message", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NO_RESULTS"))
                .andExpect(jsonPath("$.message").value("Không có kết quả"))
                .andExpect(jsonPath("$.documents").isArray());
    }

    @Test
    void doesNotExposeTechnicalDetailsWhenSearchFails() {
        DocumentAssistantService service = mock(DocumentAssistantService.class);
        when(service.respond("Java")).thenThrow(new IllegalStateException("select * from secret_table"));
        DocumentAssistantController controller = new DocumentAssistantController(service);

        DocumentAssistantResponse response = controller.search("Java");

        assertEquals("ERROR", response.type());
        assertEquals("Hiện chưa thể tải danh sách tài liệu. Bạn vui lòng thử lại sau.", response.message());
    }

    @Test
    void forwardsConversationContextWhenProvided() throws Exception {
        DocumentAssistantService service = mock(DocumentAssistantService.class);
        when(service.respond(org.mockito.ArgumentMatchers.eq("mới nhất thôi"),
                org.mockito.ArgumentMatchers.any(DocumentAssistantContext.class)))
                .thenReturn(DocumentAssistantResponse.message("RESULTS", "Đã lọc"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DocumentAssistantController(service)).build();

        mvc.perform(get("/api/document-assistant")
                        .param("message", "mới nhất thôi")
                        .param("contextKeyword", "Java")
                        .param("contextSortMode", "relevant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("RESULTS"));

        verify(service).respond(org.mockito.ArgumentMatchers.eq("mới nhất thôi"),
                org.mockito.ArgumentMatchers.argThat(context -> context.keyword().equals("Java")));
    }

    @Test
    void returnsTooManyRequestsWhenRateLimited() throws Exception {
        DocumentAssistantService service = mock(DocumentAssistantService.class);
        com.hieu.edurepo.security.DocumentAssistantRateLimiter rateLimiter = mock(com.hieu.edurepo.security.DocumentAssistantRateLimiter.class);
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(rateLimiter.getRetryAfterSeconds(org.mockito.ArgumentMatchers.anyString())).thenReturn(45L);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DocumentAssistantController(service, rateLimiter)).build();

        mvc.perform(get("/api/document-assistant").param("message", "Spam query"))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.type").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("45 giây")));
    }

    @Test
    void handlesVisionImageWithOcrSuccessfully() throws Exception {
        DocumentAssistantService service = mock(DocumentAssistantService.class);
        com.hieu.edurepo.service.OcrService ocrService = mock(com.hieu.edurepo.service.OcrService.class);
        when(ocrService.isAvailable()).thenReturn(true);
        when(ocrService.extractText(org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenReturn("Giải phương trình bậc 2: x^2 - 4 = 0");
        when(service.respond(org.mockito.ArgumentMatchers.contains("Giải phương trình")))
                .thenReturn(DocumentAssistantResponse.message("ANSWER", "Nghiệm của phương trình là x = ±2"));

        com.hieu.edurepo.security.DocumentAssistantRateLimiter rateLimiter = mock(com.hieu.edurepo.security.DocumentAssistantRateLimiter.class);
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        DocumentAssistantController controller = new DocumentAssistantController(service, rateLimiter, ocrService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        // Chuẩn bị byte header PNG hợp lệ
        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};
        org.springframework.mock.web.MockMultipartFile imageFile = new org.springframework.mock.web.MockMultipartFile(
                "image", "screenshot.png", "image/png", validPngBytes);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/document-assistant/vision")
                        .file(imageFile)
                        .param("message", "Nhờ bot giải bài này"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ANSWER"))
                .andExpect(jsonPath("$.message").value("Nghiệm của phương trình là x = ±2"));
    }

    @Test
    void rejectsInvalidMagicBytesInVisionEndpoint() throws Exception {
        DocumentAssistantService service = mock(DocumentAssistantService.class);
        DocumentAssistantController controller = new DocumentAssistantController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        // Tệp văn bản giả mạo file png
        byte[] fakeBytes = "Hello this is a plain text file pretending to be png".getBytes();
        org.springframework.mock.web.MockMultipartFile fakeFile = new org.springframework.mock.web.MockMultipartFile(
                "image", "fake.png", "image/png", fakeBytes);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/document-assistant/vision")
                        .file(fakeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void returnsNoTextFoundWhenImageHasNoTextAndNoCaption() throws Exception {
        DocumentAssistantService service = mock(DocumentAssistantService.class);
        com.hieu.edurepo.service.OcrService ocrService = mock(com.hieu.edurepo.service.OcrService.class);
        when(ocrService.isAvailable()).thenReturn(true);
        when(ocrService.extractText(org.mockito.ArgumentMatchers.any(byte[].class))).thenReturn("");

        com.hieu.edurepo.security.DocumentAssistantRateLimiter rateLimiter = mock(com.hieu.edurepo.security.DocumentAssistantRateLimiter.class);
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        DocumentAssistantController controller = new DocumentAssistantController(service, rateLimiter, ocrService);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        org.springframework.mock.web.MockMultipartFile blankImage = new org.springframework.mock.web.MockMultipartFile(
                "image", "blank.png", "image/png", validPngBytes);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/document-assistant/vision")
                        .file(blankImage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NO_TEXT_FOUND"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không nhận diện được chữ")));
    }
}
