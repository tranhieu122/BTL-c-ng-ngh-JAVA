package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.CitationDetailDto;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.repository.DocumentAssistantRepository;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.impl.DocumentAssistantServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CitationResolutionTest {

    @Test
    @DisplayName("resolveChunkPageNumber resolves from pageNumber first")
    void testResolveChunkPageNumberDirect() {
        Document doc = new Document();
        doc.setId(15L);
        DocumentChunk chunk = new DocumentChunk(doc, 1, "Some content", null, 42);
        chunk.setPageNumber(42);

        Integer page = DocumentAssistantServiceImpl.resolveChunkPageNumber(chunk);
        assertEquals(42, page);
    }

    @Test
    @DisplayName("resolveChunkPageNumber resolves from startPage when pageNumber is null")
    void testResolveChunkPageNumberStartPage() {
        Document doc = new Document();
        doc.setId(15L);
        DocumentChunk chunk = new DocumentChunk(doc, 1, "Some content", null, 42);
        chunk.setPageNumber(null);
        chunk.setStartPage(42);

        Integer page = DocumentAssistantServiceImpl.resolveChunkPageNumber(chunk);
        assertEquals(42, page);
    }

    @Test
    @DisplayName("resolveChunkPageNumber resolves from in-text header when pageNumber and startPage are null")
    void testResolveChunkPageNumberFromText() {
        Document doc = new Document();
        doc.setId(7L);
        String textWithPage = "--- Page 517 ---\nChapter 15. GraalVM Native Image Support\nSpring Boot includes support for GraalVM.";
        DocumentChunk chunk = new DocumentChunk(doc, 50, textWithPage, null, 100);
        chunk.setPageNumber(null);
        chunk.setStartPage(null);

        Integer page = DocumentAssistantServiceImpl.resolveChunkPageNumber(chunk);
        assertEquals(517, page);
    }

    @Test
    @DisplayName("getCitationDetail returns accurate CitationDetailDto with chunk and page")
    void testGetCitationDetailSuccess() {
        DocumentAssistantRepository repo = mock(DocumentAssistantRepository.class);
        DocumentChunkRepository chunkRepo = mock(DocumentChunkRepository.class);

        Document doc = new Document();
        doc.setId(15L);
        doc.setTitle("Spring Boot Reference Documentation");
        doc.setStatus(DocumentStatus.PUBLISHED);

        DocumentChunk chunk = new DocumentChunk(doc, 428, "Spring Boot supports GraalVM Native Images via AOT compilation.", null, 20);
        chunk.setId(428L);
        chunk.setPageNumber(42);
        chunk.setSectionTitle("GraalVM Native Images");

        when(chunkRepo.findById(428L)).thenReturn(Optional.of(chunk));

        DocumentAssistantServiceImpl service = new DocumentAssistantServiceImpl(
                repo, chunkRepo, null, null, null, null, null, null);

        Optional<CitationDetailDto> result = service.getCitationDetail(428L, 4, 15L);

        assertTrue(result.isPresent());
        CitationDetailDto dto = result.get();
        assertEquals(4, dto.citationIndex());
        assertEquals(15L, dto.documentId());
        assertEquals(428L, dto.chunkId());
        assertEquals(42, dto.pageNumber());
        assertEquals("Spring Boot supports GraalVM Native Images via AOT compilation.", dto.content());
        assertEquals("Spring Boot Reference Documentation", dto.title());
        assertEquals("GraalVM Native Images", dto.sectionTitle());
        assertTrue(dto.detailUrl().contains("page=42"));
        assertTrue(dto.detailUrl().contains("chunkId=428"));
    }

    @Test
    @DisplayName("getCitationDetail returns empty when chunk is not found or document is unpublished")
    void testGetCitationDetailNotFound() {
        DocumentAssistantRepository repo = mock(DocumentAssistantRepository.class);
        DocumentChunkRepository chunkRepo = mock(DocumentChunkRepository.class);

        when(chunkRepo.findById(999L)).thenReturn(Optional.empty());

        DocumentAssistantServiceImpl service = new DocumentAssistantServiceImpl(
                repo, chunkRepo, null, null, null, null, null, null);

        Optional<CitationDetailDto> result = service.getCitationDetail(999L, 4, 15L);
        assertTrue(result.isEmpty());
    }
}
