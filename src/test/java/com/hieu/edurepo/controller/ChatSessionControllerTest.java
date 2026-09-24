package com.hieu.edurepo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.dto.ChatMessageDto;
import com.hieu.edurepo.dto.ChatSessionDto;
import com.hieu.edurepo.dto.CreateChatSessionRequest;
import com.hieu.edurepo.dto.GuestMigrationRequest;
import com.hieu.edurepo.dto.UpdateChatSessionTitleRequest;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.ChatSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatSessionControllerTest {

    private ChatSessionService chatSessionService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        chatSessionService = mock(ChatSessionService.class);
        objectMapper = new ObjectMapper();

        ChatSessionController controller = new ChatSessionController(chatSessionService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();

        User user = new User();
        user.setId(42L);
        user.setUsername("testuser");
        user.setEmail("test@edurepo.com");
        user.setPassword("password");
        user.setFullName("Test User");

        CustomUserPrincipal principal = CustomUserPrincipal.from(user);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("GET /api/chat-sessions trả về 401 khi chưa đăng nhập")
    void getSessionsUnauthorizedWhenNoAuth() throws Exception {
        mvc.perform(get("/api/chat-sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/chat-sessions trả về danh sách phiên khi đã xác thực")
    void getSessionsReturnsListWhenAuthenticated() throws Exception {
        ChatSessionDto dto = new ChatSessionDto(
                1L, "Hỏi về Java 21", "GLOBAL", null, null, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now(), null
        );
        when(chatSessionService.getUserSessions(eq(42L), any())).thenReturn(List.of(dto));

        mvc.perform(get("/api/chat-sessions").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Hỏi về Java 21"));
    }

    @Test
    @DisplayName("POST /api/chat-sessions tạo mới phiên thành công")
    void createSessionSuccess() throws Exception {
        CreateChatSessionRequest request = new CreateChatSessionRequest("Hỏi AI", "GLOBAL", null, "Initial question");
        ChatSessionDto dto = new ChatSessionDto(
                2L, "Hỏi AI", "GLOBAL", null, null, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now(), null
        );
        when(chatSessionService.createSession(eq(42L), any())).thenReturn(dto);

        mvc.perform(post("/api/chat-sessions")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.title").value("Hỏi AI"));
    }

    @Test
    @DisplayName("GET /api/chat-sessions/{id}/messages trả về lịch sử tin nhắn")
    void getSessionMessagesSuccess() throws Exception {
        ChatMessageDto msgDto = new ChatMessageDto(
                10L, 1L, "USER", "Java 21?", List.of(), null, "client-1", LocalDateTime.now()
        );
        when(chatSessionService.getSessionMessages(eq(1L), eq(42L), any())).thenReturn(List.of(msgDto));

        mvc.perform(get("/api/chat-sessions/1/messages").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].content").value("Java 21?"));
    }

    @Test
    @DisplayName("PATCH /api/chat-sessions/{id}/title đổi tên phiên thành công")
    void updateSessionTitleSuccess() throws Exception {
        UpdateChatSessionTitleRequest request = new UpdateChatSessionTitleRequest("Tiêu đề mới");
        ChatSessionDto dto = new ChatSessionDto(
                1L, "Tiêu đề mới", "GLOBAL", null, null, "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now(), null
        );
        when(chatSessionService.updateSessionTitle(1L, 42L, "Tiêu đề mới")).thenReturn(dto);

        mvc.perform(patch("/api/chat-sessions/1/title")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tiêu đề mới"));
    }

    @Test
    @DisplayName("DELETE /api/chat-sessions/{id} xóa phiên thành công")
    void deleteSessionSuccess() throws Exception {
        mvc.perform(delete("/api/chat-sessions/1").principal(auth))
                .andExpect(status().isOk());

        verify(chatSessionService).deleteSession(1L, 42L);
    }

    @Test
    @DisplayName("POST /api/chat-sessions/import-guest đồng bộ phiên từ khách vãng lai")
    void importGuestSessionsSuccess() throws Exception {
        GuestMigrationRequest request = new GuestMigrationRequest(List.of(
                new GuestMigrationRequest.GuestSessionItem("guest-1", "Tiêu đề khách", "GLOBAL", null, List.of())
        ));
        when(chatSessionService.migrateGuestSessions(eq(42L), any())).thenReturn(1);

        mvc.perform(post("/api/chat-sessions/import-guest")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(1));
    }
}
