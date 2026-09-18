package com.hieu.edurepo.controller;

import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.DocumentIndexingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/rag")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRagController {

    private final DocumentIndexingService indexingService;
    private final DocumentChunkRepository chunkRepository;

    public AdminRagController(DocumentIndexingService indexingService, DocumentChunkRepository chunkRepository) {
        this.indexingService = indexingService;
        this.chunkRepository = chunkRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRagStatus() {
        long totalChunks = chunkRepository.count();
        return ResponseEntity.ok(Map.of(
                "totalChunks", totalChunks,
                "status", "READY"
        ));
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindexAll() {
        int indexedCount = indexingService.reindexAllPublishedDocuments();
        long totalChunks = chunkRepository.count();
        return ResponseEntity.ok(Map.of(
                "indexedDocuments", indexedCount,
                "totalChunks", totalChunks,
                "message", "Toàn bộ tài liệu công khai đã được lập chỉ mục RAG thành công."
        ));
    }
}
