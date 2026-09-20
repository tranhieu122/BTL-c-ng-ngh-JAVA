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
}
