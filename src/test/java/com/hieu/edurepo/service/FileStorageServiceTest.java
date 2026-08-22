package com.hieu.edurepo.service;

import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.service.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesLoadsAndDeletesFileInsideConfiguredDirectory() throws Exception {
        FileStorageServiceImpl service = new FileStorageServiceImpl(tempDirectory.toString());
        service.initialize();
        byte[] content = "document content".getBytes();
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
}
