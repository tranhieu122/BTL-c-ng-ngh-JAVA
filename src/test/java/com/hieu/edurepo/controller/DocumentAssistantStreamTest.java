package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.ChatFeedbackRequest;
import com.hieu.edurepo.entity.ChatMessageFeedback;
import com.hieu.edurepo.enums.FeedbackRating;
import com.hieu.edurepo.security.DocumentAssistantRateLimiter;
import com.hieu.edurepo.service.ChatMessageFeedbackService;
import com.hieu.edurepo.service.DocumentAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử luồng Streaming SSE (/stream) và Feedback (/feedback) trong DocumentAssistantController.
 */
class DocumentAssistantStreamTest {

    private DocumentAssistantService assistantService;
    private DocumentAssistantRateLimiter rateLimiter;
    private ChatMessageFeedbackService feedbackService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        assistantService = mock(DocumentAssistantService.class);
        rateLimiter = mock(DocumentAssistantRateLimiter.class);
        feedbackService = mock(ChatMessageFeedbackService.class);

        when(rateLimiter.tryAcquire(any())).thenReturn(true);

        DocumentAssistantController controller = new DocumentAssistantController(
                assistantService, rateLimiter, null, feedbackService);

        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Endpoint /stream trả về HTTP 200 với Content-Type text/event-stream")
    void streamEndpointReturnsEventStream() throws Exception {
        mvc.perform(get("/api/document-assistant/stream")
                        .param("message", "Cấu trúc dữ liệu là gì?"))
                .andExpect(status().isOk());

        verify(assistantService).streamResponse(eq("Cấu trúc dữ liệu là gì?"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint /stream truyền đúng scopedDocumentId khi có tham số")
    void streamEndpointForwardsScopedDocumentId() throws Exception {
        mvc.perform(get("/api/document-assistant/stream")
                        .param("message", "Định lý 1 là gì?")
                        .param("scopedDocumentId", "55"))
                .andExpect(status().isOk());

        verify(assistantService).streamResponse(eq("Định lý 1 là gì?"), any(), eq(55L), any(), any(), any());
    }

    @Test
    @DisplayName("Endpoint /feedback ghi nhận phản hồi hợp lệ")
    void feedbackEndpointAcceptsValidVote() throws Exception {
        ChatMessageFeedback feedback = new ChatMessageFeedback("msg-abc", null, "127.0.0.1", FeedbackRating.UP, null, null);
        when(feedbackService.recordFeedback(any(ChatFeedbackRequest.class), any(), any())).thenReturn(feedback);

        String jsonPayload = """
                {
                    "messageId": "msg-abc",
                    "rating": "UP"
                }
                """;

        mvc.perform(post("/api/document-assistant/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.rating").value("UP"));
    }
}
