package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentChunker;
import com.hieu.edurepo.service.DocumentIndexingService;
import com.hieu.edurepo.service.EmbeddingService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIndexingServiceImpl implements DocumentIndexingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexingServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public DocumentIndexingServiceImpl(DocumentRepository documentRepository,
                                      DocumentChunkRepository chunkRepository,
                                      FileStorageService fileStorageService,
                                      TextExtractionService textExtractionService,
                                      DocumentChunker chunker,
                                      EmbeddingService embeddingService,
                                      RagProperties ragProperties,
                                      ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.fileStorageService = fileStorageService;
        this.textExtractionService = textExtractionService;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void indexDocument(Long documentId) {
        if (documentId == null) return;
        documentRepository.findById(documentId).ifPresent(this::indexDocument);
    }

    @Override
    @Transactional
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

        // Xóa chunks cũ trước khi lập chỉ mục lại
        chunkRepository.deleteByDocumentId(docId);

        String fullText = "";
        if (document.getFilePath() != null && !document.getFilePath().isBlank()) {
            try {
                Resource resource = fileStorageService.load(document.getFilePath());
                if (resource != null && resource.exists()) {
                    try (InputStream is = resource.getInputStream()) {
                        fullText = textExtractionService.extractText(is, document.getFileName());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Could not extract text from file for document ID {}: {}", docId, e.getMessage());
            }
        }

        // Nếu nội dung file trích xuất được quá ngắn, bổ sung thêm metadata học liệu
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Tiêu đề: ").append(document.getTitle()).append("\n");
        if (document.getDescription() != null && !document.getDescription().isBlank()) {
            contentBuilder.append("Mô tả: ").append(document.getDescription()).append("\n");
        }
        if (document.getSummary() != null && !document.getSummary().isBlank()) {
            contentBuilder.append("Tóm tắt: ").append(document.getSummary()).append("\n");
        }
        if (document.getKeywords() != null && !document.getKeywords().isBlank()) {
            contentBuilder.append("Từ khóa: ").append(document.getKeywords()).append("\n");
        }
        if (document.getAuthorName() != null && !document.getAuthorName().isBlank()) {
            contentBuilder.append("Tác giả: ").append(document.getAuthorName()).append("\n");
        }

        if (fullText != null && !fullText.isBlank()) {
            contentBuilder.append("\nNội dung chi tiết:\n").append(fullText);
        }

        String completeContent = contentBuilder.toString();
        List<String> textChunks = chunker.chunkText(completeContent, ragProperties.getChunkSize(), ragProperties.getChunkOverlap());

        if (textChunks.isEmpty()) {
            LOGGER.warn("No chunks generated for document ID {}", docId);
            return;
        }

        // Tính vector embedding (batch)
        List<List<Double>> embeddings = embeddingService.embedBatch(textChunks);

        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkText = textChunks.get(i);
            String embeddingJson = null;
            if (embeddings != null && i < embeddings.size() && embeddings.get(i) != null) {
                try {
                    embeddingJson = objectMapper.writeValueAsString(embeddings.get(i));
                } catch (Exception ignored) {
                }
            }
            int estTokens = Math.max(1, chunkText.length() / 4);
            entities.add(new DocumentChunk(document, i, chunkText, embeddingJson, estTokens));
        }

        chunkRepository.saveAll(entities);
        LOGGER.info("Indexed document ID {} with {} chunks.", docId, entities.size());
    }

    @Override
    @Transactional
    public void removeIndex(Long documentId) {
        if (documentId != null) {
            chunkRepository.deleteByDocumentId(documentId);
        }
    }

    @Override
    @Transactional
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
