package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.DocumentSection;
import com.hieu.edurepo.dto.ExtractedPage;
import com.hieu.edurepo.dto.StructuredChunk;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.PageSourceType;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentChunker;
import com.hieu.edurepo.service.DocumentIndexingService;
import com.hieu.edurepo.service.EmbeddingService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.RetrievalService;
import com.hieu.edurepo.service.SectionDetectionService;
import com.hieu.edurepo.service.TextCleaningService;
import com.hieu.edurepo.service.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Triển khai Document Ingestion Pipeline nâng cao cho EduRepo.
 * Quy trình:
 * PDF -> Page Extraction & Scan Detection -> OCR (nếu cần) -> Text Cleaning -> Section Detection -> Structure-Aware Chunking -> Contextual Embedding -> Vector Storage.
 */
@Service
public class DocumentIndexingServiceImpl implements DocumentIndexingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexingServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final TextCleaningService textCleaningService;
    private final SectionDetectionService sectionDetectionService;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final RetrievalService retrievalService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public DocumentIndexingServiceImpl(DocumentRepository documentRepository,
                                       DocumentChunkRepository chunkRepository,
                                       FileStorageService fileStorageService,
                                       TextExtractionService textExtractionService,
                                       TextCleaningService textCleaningService,
                                       SectionDetectionService sectionDetectionService,
                                       DocumentChunker chunker,
                                       EmbeddingService embeddingService,
                                       RagProperties ragProperties,
                                       ObjectMapper objectMapper,
                                       RetrievalService retrievalService) {
        this(documentRepository, chunkRepository, fileStorageService, textExtractionService,
                textCleaningService, sectionDetectionService, chunker, embeddingService,
                ragProperties, objectMapper, retrievalService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentIndexingServiceImpl(DocumentRepository documentRepository,
                                       DocumentChunkRepository chunkRepository,
                                       FileStorageService fileStorageService,
                                       TextExtractionService textExtractionService,
                                       TextCleaningService textCleaningService,
                                       SectionDetectionService sectionDetectionService,
                                       DocumentChunker chunker,
                                       EmbeddingService embeddingService,
                                       RagProperties ragProperties,
                                       ObjectMapper objectMapper,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) RetrievalService retrievalService,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.fileStorageService = fileStorageService;
        this.textExtractionService = textExtractionService;
        this.textCleaningService = textCleaningService;
        this.sectionDetectionService = sectionDetectionService;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.retrievalService = retrievalService;
        this.transactionTemplate = transactionManager != null
                ? new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                : null;
    }

    @Override
    public void indexDocument(Long documentId) {
        if (documentId == null) return;
        documentRepository.findById(documentId).ifPresent(this::indexDocument);
    }

    @Override
    public void indexDocument(Document document) {
        if (document == null || document.getId() == null) {
            return;
        }

        Long docId = document.getId();

        // Nếu tài liệu chưa/không còn công khai (DRAFT, PENDING_REVIEW, REJECTED, ARCHIVED) -> xóa index
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            removeIndex(docId);
            LOGGER.info("Document ID {} is not PUBLISHED (status: {}). Chunks removed from index.", docId, document.getStatus());
            return;
        }

        // Xóa chunks cũ trong một transaction ngắn riêng biệt trước khi đọc file và gọi mạng
        executeInTransaction(() -> chunkRepository.deleteByDocumentId(docId));

        List<ExtractedPage> rawPages = new ArrayList<>();
        if (document.getFilePath() != null && !document.getFilePath().isBlank()) {
            try {
                Resource resource = fileStorageService.load(document.getFilePath());
                if (resource != null && resource.exists()) {
                    try (InputStream is = resource.getInputStream()) {
                        rawPages = textExtractionService.extractPages(is, document.getFileName());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Could not extract pages from file for document ID {}: {}", docId, e.getMessage());
            }
        }

        // 1. Text Cleaning: Tự động loại bỏ lặp header/footer, chuẩn hóa Unicode NFC, nối dấu gạch ngang
        List<ExtractedPage> cleanedPages = textCleaningService.cleanPages(rawPages);

        // 2. Section / Structure Detection: Nhận diện các chương/mục (Chương 1, 1.1, I., A.)
        List<DocumentSection> sections = sectionDetectionService.detectSections(cleanedPages, document.getTitle());

        // 3. Metadata bổ trợ học liệu (Tiêu đề, tóm tắt, mô tả, từ khóa, tác giả)
        String metadataContent = buildMetadataContent(document);
        if (!metadataContent.isBlank()) {
            List<DocumentSection> mergedSections = new ArrayList<>();
            mergedSections.add(new DocumentSection(
                    document.getTitle(),
                    "Thông tin tổng quan",
                    metadataContent,
                    1,
                    1
            ));
            mergedSections.addAll(sections);
            sections = mergedSections;
        }

        if (sections.isEmpty()) {
            LOGGER.warn("No sections or text extracted for document ID {}", docId);
            return;
        }

        // 4. Semantic & Structure-Aware Chunking: Tôn trọng ranh giới đoạn và câu văn
        List<StructuredChunk> structuredChunks = chunker.chunkSections(
                sections,
                ragProperties.getChunkSize(),
                ragProperties.getChunkOverlap()
        );

        if (structuredChunks.isEmpty()) {
            LOGGER.warn("No chunks generated for document ID {}", docId);
            return;
        }

        // 5. Contextual Embedding: Tạo vector embedding ngữ cảnh (bao gồm tiêu đề section)
        List<String> embeddingTexts = structuredChunks.stream()
                .map(StructuredChunk::getContextualContent)
                .toList();

        List<List<Double>> embeddings = embeddingService.embedBatch(embeddingTexts);
        if (embeddings == null || embeddings.isEmpty()) {
            LOGGER.warn("Embedding API returned empty for document ID {} ({} chunks). Retrieval will fallback to keyword matching.",
                    docId, structuredChunks.size());
        }

        // 6. Mapping & Database Persistence với Hierarchical Metadata
        List<DocumentChunk> entities = new ArrayList<>(structuredChunks.size());
        for (int i = 0; i < structuredChunks.size(); i++) {
            StructuredChunk sc = structuredChunks.get(i);
            String embeddingJson = null;
            if (embeddings != null && i < embeddings.size() && embeddings.get(i) != null) {
                try {
                    embeddingJson = objectMapper.writeValueAsString(embeddings.get(i));
                } catch (Exception ignored) {
                }
            }

            DocumentChunk chunk = new DocumentChunk(
                    document,
                    sc.getChunkIndex(),
                    sc.getContent(),
                    embeddingJson,
                    sc.getTokenCount()
            );

            chunk.setSectionTitle(sc.getSectionTitle());
            chunk.setSubsectionTitle(sc.getSubsectionTitle());
            chunk.setStartPage(sc.getStartPage());
            chunk.setEndPage(sc.getEndPage());
            chunk.setPageNumber(sc.getStartPage()); // Giữ tương thích 100% với citation / hover tooltip hiện có
            chunk.setSourceType(sc.getSourceType() != null ? sc.getSourceType().name() : PageSourceType.TEXT_LAYER.name());

            entities.add(chunk);
        }

        // Lưu chunks mới vào database trong transaction ngắn riêng
        executeInTransaction(() -> chunkRepository.saveAll(entities));
        LOGGER.info("Ingestion completed for document ID {} ('{}'): {} pages, {} sections, {} structured chunks created.",
                docId, document.getTitle(), rawPages.size(), sections.size(), entities.size());

        // Invalidate chunk cache để lần retrieve tiếp theo lấy dữ liệu mới
        if (retrievalService != null) {
            retrievalService.invalidateChunkCache();
        }
    }

    private void executeInTransaction(Runnable action) {
        if (action == null) return;
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }

    private String buildMetadataContent(Document document) {
        StringBuilder sb = new StringBuilder();
        if (document.getTitle() != null && !document.getTitle().isBlank()) {
            sb.append("Tiêu đề: ").append(document.getTitle()).append("\n");
        }
        if (document.getDescription() != null && !document.getDescription().isBlank()) {
            sb.append("Mô tả: ").append(document.getDescription()).append("\n");
        }
        if (document.getSummary() != null && !document.getSummary().isBlank()) {
            sb.append("Tóm tắt: ").append(document.getSummary()).append("\n");
        }
        if (document.getKeywords() != null && !document.getKeywords().isBlank()) {
            sb.append("Từ khóa: ").append(document.getKeywords()).append("\n");
        }
        if (document.getAuthorName() != null && !document.getAuthorName().isBlank()) {
            sb.append("Tác giả: ").append(document.getAuthorName()).append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public void removeIndex(Long documentId) {
        if (documentId != null) {
            executeInTransaction(() -> chunkRepository.deleteByDocumentId(documentId));
            if (retrievalService != null) {
                retrievalService.invalidateChunkCache();
            }
        }
    }

    @Override
    public int reindexAllPublishedDocuments() {
        List<Document> publishedDocs = documentRepository.findByStatus(DocumentStatus.PUBLISHED);
        LOGGER.info("Starting re-indexing for {} published documents...", publishedDocs.size());
        int count = 0;
        for (Document doc : publishedDocs) {
            try {
                indexDocument(doc);
                count++;
            } catch (Exception e) {
                LOGGER.error("Failed to index document ID {}: {}", doc.getId(), e.getMessage());
            }
        }
        LOGGER.info("Finished re-indexing {}/{} documents.", count, publishedDocs.size());
        return count;
    }
}
