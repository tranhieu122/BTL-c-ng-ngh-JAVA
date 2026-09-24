package com.hieu.edurepo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.dto.ChatSessionDto;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.entity.ChatMessage;
import com.hieu.edurepo.entity.ChatSession;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.ChatScopeType;
import com.hieu.edurepo.enums.ChatSenderType;
import com.hieu.edurepo.enums.ChatSessionStatus;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.ChatMessageRepository;
import com.hieu.edurepo.repository.ChatSessionRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.impl.ChatSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionServiceTest {

    private ChatSessionRepository sessionRepository;
    private ChatMessageRepository messageRepository;
    private UserRepository userRepository;
    private DocumentRepository documentRepository;
    private ObjectMapper objectMapper;
    private ChatSessionServiceImpl sessionService;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ChatSessionRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        userRepository = mock(UserRepository.class);
        documentRepository = mock(DocumentRepository.class);
        objectMapper = new ObjectMapper();

        sessionService = new ChatSessionServiceImpl(
                sessionRepository,
                messageRepository,
                userRepository,
                documentRepository,
                objectMapper
        );

        userA = new User();
        userA.setId(100L);
        userA.setUsername("userA");

        userB = new User();
        userB.setId(200L);
        userB.setUsername("userB");
    }

    @Test
    @DisplayName("Chống IDOR: User B không thể truy cập session của User A")
    void idorProtectionPreventsUserBAccessingUserASession() {
        Long sessionAId = 1L;
        // Session A belongs to User A
        when(sessionRepository.findByIdAndUserIdAndStatusNot(sessionAId, userB.getId(), ChatSessionStatus.DELETED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                sessionService.getSession(sessionAId, userB.getId())
        );

        assertThrows(ResourceNotFoundException.class, () ->
                sessionService.getSessionMessages(sessionAId, userB.getId(), null)
        );

        assertThrows(ResourceNotFoundException.class, () ->
                sessionService.deleteSession(sessionAId, userB.getId())
        );

        assertThrows(ResourceNotFoundException.class, () ->
                sessionService.updateSessionTitle(sessionAId, userB.getId(), "Hacked Title")
        );
    }

    @Test
    @DisplayName("User A truy cập hợp lệ session của chính mình")
    void userAAccessesOwnSessionSuccessfully() {
        Long sessionAId = 1L;
        ChatSession sessionA = new ChatSession(userA, "Nghiên cứu Spring Boot", ChatScopeType.GLOBAL, null);
        sessionA.setId(sessionAId);

        when(sessionRepository.findByIdAndUserIdAndStatusNot(sessionAId, userA.getId(), ChatSessionStatus.DELETED))
                .thenReturn(Optional.of(sessionA));

        ChatSessionDto dto = sessionService.getSession(sessionAId, userA.getId());

        assertNotNull(dto);
        assertEquals("Nghiên cứu Spring Boot", dto.title());
        assertEquals("GLOBAL", dto.scopeType());
    }

    @Test
    @DisplayName("Xóa mềm phiên (Soft Delete): Trạng thái chuyển sang DELETED, không xóa vật lý")
    void softDeleteSetsStatusToDeleted() {
        Long sessionAId = 1L;
        ChatSession sessionA = new ChatSession(userA, "Phiên cần xóa", ChatScopeType.GLOBAL, null);
        sessionA.setId(sessionAId);
        sessionA.setStatus(ChatSessionStatus.ACTIVE);

        when(sessionRepository.findByIdAndUserIdAndStatusNot(sessionAId, userA.getId(), ChatSessionStatus.DELETED))
                .thenReturn(Optional.of(sessionA));

        sessionService.deleteSession(sessionAId, userA.getId());

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(ChatSessionStatus.DELETED, captor.getValue().getStatus());
        verify(sessionRepository, never()).delete(any());
        verify(sessionRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Sinh tiêu đề tất định từ câu hỏi ban đầu (6-8 từ, không gọi LLM)")
    void deterministicTitleGeneration() {
        String question = "   Làm thế nào để cấu hình Spring Security với OAuth2 và JWT trong Java 21?   ";
        String title = sessionService.generateTitleFromQuestion(question);

        assertNotNull(title);
        // First 8 words
        assertEquals("Làm thế nào để cấu hình Spring Security", title);
        assertTrue(title.length() <= 255);

        // Test empty/null
        assertEquals("Cuộc trò chuyện mới", sessionService.generateTitleFromQuestion(""));
        assertEquals("Cuộc trò chuyện mới", sessionService.generateTitleFromQuestion(null));
        assertEquals("Cuộc trò chuyện mới", sessionService.generateTitleFromQuestion("   ???   "));
    }

    @Test
    @DisplayName("Lưu câu trả lời của trợ lý kèm citations JSON chính xác")
    void saveAssistantMessageWithCitations() {
        Long sessionId = 10L;
        ChatSession session = new ChatSession(userA, "Hỏi RAG", ChatScopeType.GLOBAL, null);
        session.setId(sessionId);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        RagSource source = new RagSource(1, 15L, 428L, "spring-boot-ref.pdf", 0.95, "/repository/15", "Sample preview text", 0, 42);
        ChatMessage msg = new ChatMessage(session, ChatSenderType.ASSISTANT, "Trả lời [1]", "[{}]", "client-123");
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(msg);

        ChatMessage saved = sessionService.saveAssistantMessage(sessionId, "Trả lời [1]", List.of(source), "client-123");

        assertNotNull(saved);
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        assertTrue(captor.getValue().getCitationsJson().contains("spring-boot-ref.pdf"));
        assertTrue(captor.getValue().getCitationsJson().contains("428"));
    }

    @Test
    @DisplayName("Tính Idempotent: clientMessageId đã tồn tại thì không ghi đè trùng lặp")
    void idempotentMessageSave() {
        Long sessionId = 10L;
        String clientMsgId = "uuid-client-123";

        ChatMessage existing = new ChatMessage();
        existing.setId(99L);
        existing.setContent("Đã lưu trước đó");

        when(messageRepository.findBySessionIdAndClientMessageId(sessionId, clientMsgId))
                .thenReturn(Optional.of(existing));

        ChatMessage result = sessionService.saveUserMessage(sessionId, "Gửi lại", clientMsgId);

        assertEquals(99L, result.getId());
        verify(messageRepository, never()).save(any());
    }
}
