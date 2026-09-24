package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.dto.ChatMessageDto;
import com.hieu.edurepo.dto.ChatSessionDto;
import com.hieu.edurepo.dto.CreateChatSessionRequest;
import com.hieu.edurepo.dto.GuestMigrationRequest;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.entity.ChatMessage;
import com.hieu.edurepo.entity.ChatSession;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.ChatScopeType;
import com.hieu.edurepo.enums.ChatSenderType;
import com.hieu.edurepo.enums.ChatSessionStatus;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.ChatMessageRepository;
import com.hieu.edurepo.repository.ChatSessionRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.ChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Triển khai nghiệp vụ quản lý phiên hội thoại đa phiên EduBot.
 */
@Service
@Transactional
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSessionServiceImpl.class);
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_TITLE_WORDS = 8;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    public ChatSessionServiceImpl(ChatSessionRepository sessionRepository,
                                  ChatMessageRepository messageRepository,
                                  UserRepository userRepository,
                                  DocumentRepository documentRepository,
                                  ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionDto> getUserSessions(Long userId, Pageable pageable) {
        if (userId == null) {
            return List.of();
        }
        Page<ChatSession> sessionPage = sessionRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, ChatSessionStatus.ACTIVE, pageable);

        return sessionPage.getContent().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public ChatSessionDto createSession(Long userId, CreateChatSessionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại."));

        String title = request.title();
        if (title == null || title.isBlank()) {
            title = generateTitleFromQuestion(request.initialQuestion());
        } else {
            title = title.strip();
            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH);
            }
        }

        ChatScopeType scopeType = parseScopeType(request.scopeType());
        Document scopedDoc = null;
        if (request.scopedDocumentId() != null) {
            scopedDoc = documentRepository.findById(request.scopedDocumentId()).orElse(null);
            if (scopedDoc != null) {
                scopeType = ChatScopeType.DOCUMENT;
            }
        }

        ChatSession session = new ChatSession(user, title, scopeType, scopedDoc);
        ChatSession saved = sessionRepository.save(session);
        LOGGER.info("Created new ChatSession [id={}] for user [id={}], title='{}'", saved.getId(), userId, saved.getTitle());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessionDto getSession(Long sessionId, Long userId) {
        ChatSession session = findAuthorizedSession(sessionId, userId);
        return toDto(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getSessionMessages(Long sessionId, Long userId, Pageable pageable) {
        // Kiểm tra quyền sở hữu chống IDOR
        findAuthorizedSession(sessionId, userId);

        Page<ChatMessage> messagePage = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId, pageable);
        return messagePage.getContent().stream()
                .map(this::toMessageDto)
                .toList();
    }

    @Override
    public ChatSessionDto updateSessionTitle(Long sessionId, Long userId, String title) {
        ChatSession session = findAuthorizedSession(sessionId, userId);

        String cleanTitle = (title != null && !title.isBlank()) ? title.strip() : "Cuộc trò chuyện mới";
        if (cleanTitle.length() > MAX_TITLE_LENGTH) {
            cleanTitle = cleanTitle.substring(0, MAX_TITLE_LENGTH);
        }

        session.setTitle(cleanTitle);
        session.touch();
        ChatSession updated = sessionRepository.save(session);
        LOGGER.info("Updated ChatSession [id={}] title to '{}' by user [id={}]", sessionId, cleanTitle, userId);
        return toDto(updated);
    }

    @Override
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = findAuthorizedSession(sessionId, userId);
        session.setStatus(ChatSessionStatus.DELETED);
        session.touch();
        sessionRepository.save(session);
        LOGGER.info("Soft-deleted ChatSession [id={}] by user [id={}]", sessionId, userId);
    }

    @Override
    public ChatSession getOrCreateActiveSession(Long userId, Long sessionId, String firstQuestion, Long scopedDocumentId) {
        if (sessionId != null) {
            Optional<ChatSession> existing = sessionRepository.findByIdAndUserIdAndStatusNot(
                    sessionId, userId, ChatSessionStatus.DELETED);
            if (existing.isPresent()) {
                return existing.get();
            }
            LOGGER.warn("Session [id={}] not found or not owned by user [id={}]. Auto-creating replacement session.", sessionId, userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại."));

        String title = generateTitleFromQuestion(firstQuestion);
        ChatScopeType scopeType = ChatScopeType.GLOBAL;
        Document scopedDoc = null;
        if (scopedDocumentId != null) {
            scopedDoc = documentRepository.findById(scopedDocumentId).orElse(null);
            if (scopedDoc != null) {
                scopeType = ChatScopeType.DOCUMENT;
            }
        }

        ChatSession newSession = new ChatSession(user, title, scopeType, scopedDoc);
        return sessionRepository.save(newSession);
    }

    @Override
    public ChatMessage saveUserMessage(Long sessionId, String content, String clientMessageId) {
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            Optional<ChatMessage> dup = messageRepository.findBySessionIdAndClientMessageId(sessionId, clientMessageId);
            if (dup.isPresent()) {
                return dup.get();
            }
        }

        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên không tồn tại."));

        ChatMessage message = new ChatMessage(session, ChatSenderType.USER, content, null, clientMessageId);
        ChatMessage saved = messageRepository.save(message);

        session.touch();
        sessionRepository.save(session);
        return saved;
    }

    @Override
    public ChatMessage saveAssistantMessage(Long sessionId, String content, List<RagSource> citations, String clientMessageId) {
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            Optional<ChatMessage> dup = messageRepository.findBySessionIdAndClientMessageId(sessionId, clientMessageId);
            if (dup.isPresent()) {
                return dup.get();
            }
        }

        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên không tồn tại."));

        String citationsJson = null;
        if (citations != null && !citations.isEmpty()) {
            try {
                citationsJson = objectMapper.writeValueAsString(citations);
            } catch (Exception e) {
                LOGGER.warn("Could not serialize citations for session {}: {}", sessionId, e.getMessage());
            }
        }

        ChatMessage message = new ChatMessage(session, ChatSenderType.ASSISTANT, content, citationsJson, clientMessageId);
        ChatMessage saved = messageRepository.save(message);

        session.touch();
        sessionRepository.save(session);
        return saved;
    }

    @Override
    public void updateMessageFeedback(Long sessionId, Long messageId, Long userId, Integer rating) {
        findAuthorizedSession(sessionId, userId);

        ChatMessage message = messageRepository.findByIdAndSessionId(messageId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Tin nhắn không tồn tại trong phiên này."));

        message.setFeedbackRating(rating);
        messageRepository.save(message);
    }

    @Override
    public int migrateGuestSessions(Long userId, GuestMigrationRequest request) {
        if (userId == null || request == null || request.sessions() == null || request.sessions().isEmpty()) {
            return 0;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại."));

        int importedCount = 0;
        for (GuestMigrationRequest.GuestSessionItem item : request.sessions()) {
            if (item.messages() == null || item.messages().isEmpty()) {
                continue;
            }

            // Tạo session cho user
            String title = (item.title() != null && !item.title().isBlank())
                    ? item.title().strip()
                    : generateTitleFromQuestion(item.messages().get(0).content());

            ChatScopeType scopeType = parseScopeType(item.scopeType());
            Document scopedDoc = null;
            if (item.scopedDocumentId() != null) {
                scopedDoc = documentRepository.findById(item.scopedDocumentId()).orElse(null);
                if (scopedDoc != null) scopeType = ChatScopeType.DOCUMENT;
            }

            ChatSession session = new ChatSession(user, title, scopeType, scopedDoc);
            ChatSession savedSession = sessionRepository.save(session);

            // Import từng message
            for (GuestMigrationRequest.GuestMessageItem msgItem : item.messages()) {
                ChatSenderType senderType = "user".equalsIgnoreCase(msgItem.senderType())
                        ? ChatSenderType.USER
                        : ChatSenderType.ASSISTANT;

                String citationsJson = null;
                if (msgItem.citations() != null && !msgItem.citations().isEmpty()) {
                    try {
                        citationsJson = objectMapper.writeValueAsString(msgItem.citations());
                    } catch (Exception ignored) {}
                }

                ChatMessage message = new ChatMessage(
                        savedSession,
                        senderType,
                        msgItem.content() != null ? msgItem.content() : "",
                        citationsJson,
                        msgItem.clientMessageId()
                );
                message.setFeedbackRating(msgItem.rating());
                messageRepository.save(message);
            }

            importedCount++;
        }

        LOGGER.info("Migrated {} guest sessions for user [id={}]", importedCount, userId);
        return importedCount;
    }

    @Override
    public String generateTitleFromQuestion(String question) {
        if (question == null || question.isBlank()) {
            return "Cuộc trò chuyện mới";
        }

        String normalized = question.replaceAll("\\s+", " ").trim();
        String[] words = normalized.split(" ");

        String candidateTitle;
        if (words.length <= MAX_TITLE_WORDS) {
            candidateTitle = normalized;
        } else {
            candidateTitle = String.join(" ", Arrays.copyOfRange(words, 0, MAX_TITLE_WORDS));
        }

        // Loại bỏ dấu câu thừa ở cuối nếu có
        candidateTitle = candidateTitle.replaceAll("[,.?:;!\\-]+$", "").trim();

        if (candidateTitle.length() > MAX_TITLE_LENGTH) {
            candidateTitle = candidateTitle.substring(0, MAX_TITLE_LENGTH);
        }

        return candidateTitle.isEmpty() ? "Cuộc trò chuyện mới" : candidateTitle;
    }

    private ChatSession findAuthorizedSession(Long sessionId, Long userId) {
        return sessionRepository.findByIdAndUserIdAndStatusNot(sessionId, userId, ChatSessionStatus.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Phiên trò chuyện không tồn tại hoặc bạn không có quyền truy cập."));
    }

    private ChatSessionDto toDto(ChatSession session) {
        Long docId = session.getScopedDocument() != null ? session.getScopedDocument().getId() : null;
        String docTitle = session.getScopedDocument() != null ? session.getScopedDocument().getTitle() : null;

        return new ChatSessionDto(
                session.getId(),
                session.getTitle(),
                session.getScopeType().name(),
                docId,
                docTitle,
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                null
        );
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        List<RagSource> citations = parseCitationsJson(message.getCitationsJson());
        return new ChatMessageDto(
                message.getId(),
                message.getSession().getId(),
                message.getSenderType().name(),
                message.getContent(),
                citations,
                message.getFeedbackRating(),
                message.getClientMessageId(),
                message.getCreatedAt()
        );
    }

    private List<RagSource> parseCitationsJson(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationsJson, new TypeReference<List<RagSource>>() {});
        } catch (Exception e) {
            LOGGER.warn("Failed to deserialize citations JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private ChatScopeType parseScopeType(String value) {
        if ("DOCUMENT".equalsIgnoreCase(value)) {
            return ChatScopeType.DOCUMENT;
        }
        return ChatScopeType.GLOBAL;
    }
}
