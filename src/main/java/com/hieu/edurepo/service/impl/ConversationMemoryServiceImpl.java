package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.ChatMessage;
import com.hieu.edurepo.enums.ChatSenderType;
import com.hieu.edurepo.repository.ChatMessageRepository;
import com.hieu.edurepo.service.ConversationMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Triển khai dịch vụ trích xuất và chuẩn hóa bộ nhớ ngữ cảnh hội thoại đa lượt (Multi-turn Memory).
 */
@Service
@Transactional(readOnly = true)
public class ConversationMemoryServiceImpl implements ConversationMemoryService {

    private static final int DEFAULT_MAX_TURNS = 4; // 4 lượt hỏi-đáp gần nhất
    private static final int MAX_TOTAL_CHARS = 4000; // Giới hạn tối đa ~1,000 tokens
    private static final int MAX_ASSISTANT_MSG_CHARS = 450; // Cắt ngắn câu trả lời cũ của AI để tiết kiệm token

    private final ChatMessageRepository chatMessageRepository;

    public ConversationMemoryServiceImpl(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public String getConversationHistoryForPrompt(Long sessionId, int maxTurns) {
        if (sessionId == null) {
            return "";
        }

        int turns = maxTurns > 0 ? maxTurns : DEFAULT_MAX_TURNS;
        // Mỗi turn gồm 1 câu User và 1 câu Assistant -> Số message cần lấy tối đa là turns * 2
        int limitMessages = turns * 2;

        List<ChatMessage> recentDesc = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        if (recentDesc == null || recentDesc.isEmpty()) {
            return "";
        }

        // Lấy tối đa limitMessages tin nhắn gần nhất và đảo ngược lại thứ tự thời gian tăng dần
        List<ChatMessage> selectedDesc = recentDesc.size() > limitMessages
                ? recentDesc.subList(0, limitMessages)
                : recentDesc;

        List<ChatMessage> chronological = new ArrayList<>(selectedDesc);
        Collections.reverse(chronological);

        StringBuilder sb = new StringBuilder();
        int totalChars = 0;

        for (ChatMessage msg : chronological) {
            if (msg.getSenderType() == ChatSenderType.SYSTEM) {
                continue;
            }

            String role = msg.getSenderType() == ChatSenderType.USER ? "User" : "Assistant";
            String rawContent = msg.getContent() != null ? msg.getContent().strip() : "";

            // Nếu câu trả lời của trợ lý quá dài, cắt ngắn để bảo vệ token
            String cleanedContent = rawContent;
            if (msg.getSenderType() == ChatSenderType.ASSISTANT && cleanedContent.length() > MAX_ASSISTANT_MSG_CHARS) {
                cleanedContent = cleanedContent.substring(0, MAX_ASSISTANT_MSG_CHARS) + "…";
            }

            String line = role + ": " + cleanedContent + "\n";
            if (totalChars + line.length() > MAX_TOTAL_CHARS && sb.length() > 0) {
                break;
            }

            sb.append(line);
            totalChars += line.length();
        }

        return sb.toString().strip();
    }
}
