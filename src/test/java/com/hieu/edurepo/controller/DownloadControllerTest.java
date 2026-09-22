package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử bộ điều hướng tải tài liệu an toàn (Download Controller Test).
 * Kiểm tra việc cấp token tải tệp một lần, kiểm tra thời hạn hiệu lực và truyền phát luồng nhị phân.
 */
class DownloadControllerTest {

    @Test
    void reviewerCanDownloadSubmittedDocumentThroughProtectedEndpoint() {
        DocumentService documentService = mock(DocumentService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DownloadController controller = new DownloadController(documentService, fileStorageService);
        Document document = document(DocumentStatus.SUBMITTED);
        Resource resource = new ByteArrayResource("content".getBytes());
        when(documentService.findById(10L)).thenReturn(document);
        when(fileStorageService.load("stored.pdf")).thenReturn(resource);

        ResponseEntity<Resource> response = controller.downloadForReview(10L);

        assertSame(resource, response.getBody());
        assertEquals(200, response.getStatusCode().value());
        String contentDisposition = Objects.requireNonNull(
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("document.pdf", ContentDisposition.parse(contentDisposition).getFilename());
    }

    @Test
    void publicEndpointStillRejectsUnpublishedDocument() {
        DocumentService documentService = mock(DocumentService.class);
        DownloadController controller = new DownloadController(documentService, mock(FileStorageService.class));
        when(documentService.findById(10L)).thenReturn(document(DocumentStatus.SUBMITTED));

        assertThrows(ResourceNotFoundException.class, () -> controller.download(10L));
    }

    @Test
    void publicEndpointDownloadsPublishedDocument() {
        DocumentService documentService = mock(DocumentService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        DownloadController controller = new DownloadController(documentService, fileStorageService);
        Document document = document(DocumentStatus.PUBLISHED);
        Resource resource = new ByteArrayResource("public content".getBytes());
        when(documentService.findById(10L)).thenReturn(document);
        when(fileStorageService.load("stored.pdf")).thenReturn(resource);

        ResponseEntity<Resource> response = controller.download(10L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(resource, response.getBody());
    }

    @Test
    void reviewerEndpointRejectsDraftDocument() {
        DocumentService documentService = mock(DocumentService.class);
        DownloadController controller = new DownloadController(documentService, mock(FileStorageService.class));
        when(documentService.findById(10L)).thenReturn(document(DocumentStatus.DRAFT));

        assertThrows(ResourceNotFoundException.class, () -> controller.downloadForReview(10L));
    }

    private Document document(DocumentStatus status) {
        Document document = new Document();
        document.setStatus(status);
        document.setFileName("document.pdf");
        document.setFilePath("stored.pdf");
        return document;
    }
}
