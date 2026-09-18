package com.hieu.edurepo.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentChunker {

    public List<String> chunkText(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String cleaned = text.trim();
        if (cleaned.length() <= chunkSize) {
            return List.of(cleaned);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int textLength = cleaned.length();
        int safeOverlap = Math.max(0, Math.min(chunkOverlap, chunkSize / 2));

        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            if (end < textLength) {
                // Cố gắng tìm điểm kết thúc câu tự nhiên gần nhất
                int splitIndex = findBestSplitPoint(cleaned, start, end);
                if (splitIndex > start + (chunkSize / 3)) {
                    end = splitIndex;
                }
            }

            String chunk = cleaned.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= textLength) {
                break;
            }

            // Bước nhảy tiếp theo gối đầu (overlap)
            start = Math.max(start + 1, end - safeOverlap);
        }

        return chunks;
    }

    private int findBestSplitPoint(String text, int start, int targetEnd) {
        // 1. Tìm ranh giới kết thúc đoạn (\n\n)
        for (int i = targetEnd; i >= targetEnd - 100 && i > start; i--) {
            if (i < text.length() - 1 && text.charAt(i) == '\n' && text.charAt(i + 1) == '\n') {
                return i + 2;
            }
        }

        // 2. Tìm ranh giới kết thúc câu (. hoặc ? hoặc ! theo sau là dấu cách)
        for (int i = targetEnd; i >= targetEnd - 80 && i > start; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '?' || c == '!') && i < text.length() - 1 && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }

        // 3. Tìm ranh giới ngắt dòng đơn (\n)
        for (int i = targetEnd; i >= targetEnd - 60 && i > start; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }

        // 4. Tìm khoảng trắng thông thường
        for (int i = targetEnd; i >= targetEnd - 40 && i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }

        return targetEnd;
    }
}
