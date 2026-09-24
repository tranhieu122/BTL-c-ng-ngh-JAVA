package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.ChatMessage;
import com.hieu.edurepo.enums.ChatSenderType;
import com.hieu.edurepo.repository.ChatMessageRepository;
import com.hieu.edurepo.service.impl.ConversationMemoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {

    private ChatMessageRepository chatMessageRepository;
    private ConversationMemoryService memoryService;

    @BeforeEach
    void setUp() {
        chatMessageRepository = mock(ChatMessageRepository.class);
        memoryService = new ConversationMemoryServiceImpl(chatMessageRepository);
    }

    @Test
    @DisplayName("Empty messages list returns empty string")
    void emptyListReturnsEmptyString() {
        when(chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        String result = memoryService.getConversationHistoryForPrompt(1L, 4);
        assertEquals("", result);
    }

    @Test
    @DisplayName("Formats conversation history within sliding window turns")
    void formatsTurnsCorrectly() {
        Long sessionId = 1L;
        // Repository returns newest first (DESC)
        List<ChatMessage> descList = List.of(
                new ChatMessage(null, ChatSenderType.ASSISTANT, "Java 21 giới thiệu Virtual Threads và Pattern Matching.", null, null),
                new ChatMessage(null, ChatSenderType.USER, "Java 21 có gì mới?", null, null),
                new ChatMessage(null, ChatSenderType.ASSISTANT, "Chào bạn! Tôi có thể giúp gì?", null, null),
                new ChatMessage(null, ChatSenderType.USER, "Xin chào AI", null, null)
        );

        when(chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(descList);

        String history = memoryService.getConversationHistoryForPrompt(sessionId, 4);

        assertTrue(history.contains("User: Xin chào AI"));
        assertTrue(history.contains("Assistant: Chào bạn! Tôi có thể giúp gì?"));
        assertTrue(history.contains("User: Java 21 có gì mới?"));
        assertTrue(history.contains("Assistant: Java 21 giới thiệu Virtual Threads và Pattern Matching."));
    }

    @Test
    @DisplayName("Truncates long assistant replies to keep token overhead low")
    void truncatesLongAssistantReplies() {
        Long sessionId = 1L;
        String longText = "A".repeat(600);
        List<ChatMessage> descList = List.of(
                new ChatMessage(null, ChatSenderType.ASSISTANT, longText, null, null),
                new ChatMessage(null, ChatSenderType.USER, "Giải thích chi tiết", null, null)
        );

        when(chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(descList);

        String history = memoryService.getConversationHistoryForPrompt(sessionId, 4);

        assertTrue(history.contains("User: Giải thích chi tiết"));
        assertTrue(history.contains("Assistant: " + "A".repeat(450) + "…"));
        assertFalse(history.contains("A".repeat(500)));
    }

    @Test
    @DisplayName("Respects maximum sliding window limit of 3 turns (6 messages)")
    void respectsTurnLimit() {
        Long sessionId = 1L;
        // 5 turns (10 messages), newest first
        List<ChatMessage> descList = new ArrayList<>();
        for (int i = 5; i >= 1; i--) {
            descList.add(new ChatMessage(null, ChatSenderType.ASSISTANT, "Trả lời " + i, null, null));
            descList.add(new ChatMessage(null, ChatSenderType.USER, "Câu hỏi " + i, null, null));
        }

        when(chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(descList);

        // Max 3 turns = 6 messages (turns 3, 4, 5)
        String history = memoryService.getConversationHistoryForPrompt(sessionId, 3);

        // Turn 1 and 2 should be pruned
        assertFalse(history.contains("Câu hỏi 1"));
        assertFalse(history.contains("Trả lời 1"));
        assertFalse(history.contains("Câu hỏi 2"));
        assertFalse(history.contains("Trả lời 2"));

        // Turns 3, 4, 5 should be present
        assertTrue(history.contains("Câu hỏi 3"));
        assertTrue(history.contains("Trả lời 3"));
        assertTrue(history.contains("Câu hỏi 4"));
        assertTrue(history.contains("Trả lời 4"));
        assertTrue(history.contains("Câu hỏi 5"));
        assertTrue(history.contains("Trả lời 5"));
    }
}
