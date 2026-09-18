package com.hieu.edurepo.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void testEmptyOrBlankText() {
        assertTrue(chunker.chunkText("", 500, 100).isEmpty());
        assertTrue(chunker.chunkText(null, 500, 100).isEmpty());
        assertTrue(chunker.chunkText("   \n\t  ", 500, 100).isEmpty());
    }

    @Test
    void testShortTextReturnsSingleChunk() {
        String shortText = "EduRepo là hệ thống quản lý học liệu trực tuyến hiện đại.";
        List<String> chunks = chunker.chunkText(shortText, 500, 100);
        assertEquals(1, chunks.size());
        assertEquals(shortText, chunks.get(0));
    }

    @Test
    void testLongTextChunkingWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            sb.append("Câu số ").append(i).append(" mô tả chi tiết nội dung học phần Spring Boot và Java Backend. ");
        }
        String text = sb.toString();

        List<String> chunks = chunker.chunkText(text, 200, 40);
        assertTrue(chunks.size() > 1);

        for (String chunk : chunks) {
            assertFalse(chunk.isBlank());
            assertTrue(chunk.length() <= 260); // Trong giới hạn chunk size + ranh giới
        }
    }
}
