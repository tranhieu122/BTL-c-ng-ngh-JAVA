package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
@Controller
public class DownloadController {

    private final DocumentService documentService;
    private final FileStorageService fileStorageService;

    public DownloadController(DocumentService documentService, FileStorageService fileStorageService) {
        this.documentService = documentService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Tài liệu chưa được công bố");
        }
        return createDownloadResponse(document);
    }

    @GetMapping("/reviews/{id}/download")
    public ResponseEntity<Resource> downloadForReview(@PathVariable Long id) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.SUBMITTED
                && document.getStatus() != DocumentStatus.APPROVED) {
            throw new ResourceNotFoundException("Tài liệu không nằm trong hàng chờ kiểm duyệt");
        }
        return createDownloadResponse(document);
    }

    private ResponseEntity<Resource> createDownloadResponse(Document document) {
        Resource resource = fileStorageService.load(requireDocumentValue(document.getFilePath(), "Thiếu đường dẫn tệp"));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(requireDocumentValue(document.getFileName(), "Thiếu tên tệp"), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private String requireDocumentValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new FileStorageException(message);
        }
        return value;
    }
}
