package com.hieu.edurepo.config;

import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.DocumentIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class RagIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagIndexInitializer.class);

    private final DocumentChunkRepository chunkRepository;
    private final DocumentIndexingService indexingService;
    private final RagProperties ragProperties;

    public RagIndexInitializer(DocumentChunkRepository chunkRepository,
                               DocumentIndexingService indexingService,
                               RagProperties ragProperties) {
        this.chunkRepository = chunkRepository;
        this.indexingService = indexingService;
        this.ragProperties = ragProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.isEnabled()) {
            LOGGER.info("RAG is disabled by configuration.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                long currentChunks = chunkRepository.count();
                if (currentChunks == 0) {
                    LOGGER.info("No document chunks detected in RAG index. Triggering initial background indexing...");
                    int indexed = indexingService.reindexAllPublishedDocuments();
                    LOGGER.info("Initial RAG indexing completed: {} documents indexed.", indexed);
                } else {
                    LOGGER.info("RAG index ready with {} existing chunks.", currentChunks);
                }
            } catch (Exception e) {
                LOGGER.warn("Initial RAG background indexing encountered an issue: {}", e.getMessage());
            }
        });
    }
}
