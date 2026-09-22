package com.hieu.edurepo.service;

import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.service.impl.FileStorageServiceImpl;
import com.hieu.edurepo.observability.OperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử dịch vụ lưu trữ tệp tin vật lý trên ổ đĩa (File Storage Service Test).
 * Xác minh an toàn thư mục, ngăn chặn tấn công Path Traversal và sinh tên tệp duy nhất bằng UUID.
 */
class FileStorageServiceTest {

    @Test
    void rejectsBlankUploadDirectoryBeforeResolvingItToTheWorkingDirectory() {
        assertThrows(IllegalStateException.class, () -> new FileStorageServiceImpl("  "));
    }

    @Test
    void closesEveryOpenedStreamAndRejectsFakeContent() throws Exception {
        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString());
        service.initialize();
        java.util.List<Boolean> closed = new java.util.ArrayList<>();
        var file = new MockMultipartFile("file", "tracked.pdf", "application/pdf", "%PDF-1.7 test".getBytes()) {
            @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                int index = closed.size(); closed.add(false);
                return new java.io.FilterInputStream(super.getInputStream()) {
                    @Override public void close() throws java.io.IOException { closed.set(index, true); super.close(); }
                };
            }
        };
        service.store(file);
        assertFalse(closed.isEmpty());
        assertTrue(closed.stream().allMatch(Boolean::booleanValue));
        var fake = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a PDF".getBytes());
        assertThrows(FileStorageException.class, () -> service.store(fake));
    }

    @Test
    void failedCopyRemovesPartialFileAndClosesInput() throws Exception {
        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString());
        service.initialize();
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var file = new MockMultipartFile("file", "broken.pdf", "application/pdf", "%PDF-1.7".getBytes()) {
            @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                if (opened.incrementAndGet() == 1) return super.getInputStream();
                return new java.io.InputStream() {
                    @Override public int read() throws java.io.IOException { throw new java.io.IOException("simulated disk read failure"); }
                    @Override public void close() { closed.set(true); }
                };
            }
        };
        assertThrows(FileStorageException.class, () -> service.store(file));
        assertTrue(closed.get());
        try (var files = Files.list(tempDirectory)) { assertEquals(0, files.count()); }
    }

    Path tempDirectory;

    @BeforeEach
    void createWorkspaceLocalTestDirectory() throws Exception {
        tempDirectory = Path.of("target", "test-work", "file-storage-" + UUID.randomUUID())
                .toAbsolutePath();
        Files.createDirectories(tempDirectory);
    }

    @Test
    void storesLoadsAndDeletesFileInsideConfiguredDirectory() throws Exception {
        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString());
        service.initialize();
        byte[] content = "%PDF-1.7 document content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", content);

        String storedName = service.store(file);
        Path storedPath = tempDirectory.resolve(storedName);
        Resource resource = service.load(storedName);

        assertTrue(Files.exists(storedPath));
        assertTrue(resource.isReadable());
        assertArrayEquals(content, resource.getContentAsByteArray());

        service.delete(storedName);
        assertFalse(Files.exists(storedPath));
        assertThrows(ResourceNotFoundException.class, () -> service.load(storedName));
    }

    @Test
    void rejectsPathTraversalOutsideUploadDirectory() {
        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString());
        service.initialize();

        assertThrows(FileStorageException.class, () -> service.load("../outside.pdf"));
        assertThrows(FileStorageException.class, () -> service.delete("../outside.pdf"));
    }

    @Test
    void scannerCanRejectStagedFileWithoutLeavingArtifacts() throws Exception {
        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString(),
                10L * 1024 * 1024, (file, originalName) -> false);
        service.initialize();
        var file = new MockMultipartFile("file", "unsafe.pdf", "application/pdf", "%PDF-1.7 payload".getBytes());

        assertThrows(FileStorageException.class, () -> service.store(file));
        try (var files = Files.list(tempDirectory)) { assertEquals(0, files.count()); }
    }

    @Test
    void uploadMetricsUseOnlyBoundedReasonTags() {
        var registry = new SimpleMeterRegistry();
        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString(),
                10L * 1024 * 1024, (file, originalName) -> true, new OperationalMetrics(registry));
        service.initialize();
        var file = new MockMultipartFile("file", "private-name.exe", "application/octet-stream", "payload".getBytes());

        assertThrows(FileStorageException.class, () -> service.store(file));

        assertEquals(1.0, registry.get("edurepo.upload.completed")
                .tag("result", "REJECTED").tag("reason", "UNSUPPORTED_EXTENSION").counter().count());
        assertTrue(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> tag.getValue().contains("private-name") || tag.getKey().contains("filename")));
    }

    @Test
    void startupRemovesOnlyInterruptedUploadStagingFiles() throws Exception {
        Path interrupted = tempDirectory.resolve(".upload-" + UUID.randomUUID() + ".tmp");
        Path document = tempDirectory.resolve("existing.pdf");
        Files.writeString(interrupted, "partial");
        Files.writeString(document, "%PDF-1.7 existing");

        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString());
        service.initialize();

        assertFalse(Files.exists(interrupted));
        assertTrue(Files.exists(document));
    }
}
