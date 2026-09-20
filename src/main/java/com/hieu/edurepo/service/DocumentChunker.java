package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentSection;
import com.hieu.edurepo.dto.StructuredChunk;
import com.hieu.edurepo.enums.PageSourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bộ chia nhỏ văn bản (Semantic & Structure-Aware Document Chunker).
 * Chia nhỏ văn bản theo cấu trúc ngữ nghĩa tự nhiên (Section -> Paragraph -> Sentence) thay vì cắt thô theo ký tự.
 */
@Component
public class DocumentChunker {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?\\n])\\s+(?=[A-Z\u00C0-\u1EF90-9\"'\\-])");

    /**
     * Chia nhỏ theo cấu trúc phân đoạn (Sections & Subsections), bảo toàn ranh giới trang và tiêu đề chương mục.
     *
     * @param sections Danh sách các phân đoạn của tài liệu
     * @param chunkSize Kích thước mục tiêu tối đa của 1 chunk (ký tự)
     * @param chunkOverlap Ký tự gối đầu giữa 2 chunk liền kề
     * @return Danh sách các StructuredChunk có đầy đủ metadata phân cấp
     */
    public List<StructuredChunk> chunkSections(List<DocumentSection> sections, int chunkSize, int chunkOverlap) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }

        List<StructuredChunk> result = new ArrayList<>();
        int runningIndex = 0;
        int safeOverlap = Math.max(0, Math.min(chunkOverlap, chunkSize / 3));

        for (DocumentSection section : sections) {
            String content = section.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            List<String> sectionChunks = splitIntoSemanticChunks(content, chunkSize, safeOverlap);
            for (String chunkText : sectionChunks) {
                if (chunkText.isBlank()) {
                    continue;
                }

                int estTokens = Math.max(1, chunkText.length() / 4);
                StructuredChunk sc = new StructuredChunk(
                        runningIndex++,
                        chunkText,
                        section.getSectionTitle(),
                        section.getSubsectionTitle(),
                        section.getStartPage(),
                        section.getEndPage(),
                        PageSourceType.TEXT_LAYER,
                        estTokens
                );
                result.add(sc);
            }
        }

        return result;
    }

    /**
     * Chia một đoạn văn bản thành các chunk tôn trọng ranh giới đoạn văn (paragraph) và câu (sentence).
     */
    private List<String> splitIntoSemanticChunks(String text, int chunkSize, int overlap) {
        String trimmed = text.trim();
        if (trimmed.length() <= chunkSize) {
            return List.of(trimmed);
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_SPLIT.split(trimmed);

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }

            // Nếu đoạn văn dài hơn cả chunkSize, phải cắt nhỏ theo từng câu
            if (p.length() > chunkSize) {
                // Đẩy chunk hiện tại ra nếu đã có nội dung
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }

                List<String> subChunks = splitParagraphBySentences(p, chunkSize, overlap);
                chunks.addAll(subChunks);
                continue;
            }

            // Nếu gộp đoạn văn này vào vượt quá chunkSize
            if (currentChunk.length() > 0 && (currentChunk.length() + p.length() + 2 > chunkSize)) {
                chunks.add(currentChunk.toString().trim());

                // Tính toán overlap từ cuối chunk trước nếu có
                String overlapPrefix = getOverlapTail(currentChunk.toString(), overlap);
                currentChunk.setLength(0);
                if (!overlapPrefix.isEmpty()) {
                    currentChunk.append(overlapPrefix).append("\n\n");
                }
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(p);
        }

        if (currentChunk.length() > 0 && !currentChunk.toString().isBlank()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * Chia đoạn văn bản dài hơn chunkSize thành các câu hoàn chỉnh, tránh cắt giữa chừng từ ngữ.
     */
    private List<String> splitParagraphBySentences(String paragraph, int chunkSize, int overlap) {
        String[] sentences = SENTENCE_SPLIT.split(paragraph);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String s : sentences) {
            String sentence = s.trim();
            if (sentence.isEmpty()) continue;

            if (sentence.length() > chunkSize) {
                // Trường hợp câu đơn lẻ cực dài không có dấu chấm: fallback sang chunkText kinh điển
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(chunkText(sentence, chunkSize, overlap));
                continue;
            }

            if (current.length() > 0 && (current.length() + sentence.length() + 1 > chunkSize)) {
                chunks.add(current.toString().trim());
                String overlapTail = getOverlapTail(current.toString(), overlap);
                current.setLength(0);
                if (!overlapTail.isEmpty()) {
                    current.append(overlapTail).append(" ");
                }
            }

            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(sentence);
        }

        if (current.length() > 0 && !current.toString().isBlank()) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    private String getOverlapTail(String text, int overlapLength) {
        if (overlapLength <= 0 || text == null || text.length() <= overlapLength) {
            return "";
        }
        int start = Math.max(0, text.length() - overlapLength);
        // Tìm khoảng trắng gần nhất để không cắt đứt từ
        int firstSpace = text.indexOf(' ', start);
        if (firstSpace != -1 && firstSpace < text.length() - 10) {
            return text.substring(firstSpace + 1).trim();
        }
        return text.substring(start).trim();
    }

    /**
     * Phương thức phân đoạn văn bản cơ bản (Classic sliding window với split point heuristic).
     * Giữ nguyên 100% tương thích ngược với các service và unit test hiện có.
     */
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
