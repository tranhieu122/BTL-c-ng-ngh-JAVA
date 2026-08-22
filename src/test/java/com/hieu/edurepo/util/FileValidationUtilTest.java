package com.hieu.edurepo.util;

import com.hieu.edurepo.exception.FileStorageException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileValidationUtilTest {

    @Test
    void acceptsSupportedExtensionIgnoringCase() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.PDF", "application/pdf", "content".getBytes());

        assertEquals("pdf", FileValidationUtil.validateAndGetExtension(file));
    }

    @Test
    void rejectsEmptyUnsupportedAndOverlongFileNames() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);
        MockMultipartFile unsupported = new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", "content".getBytes());
        MockMultipartFile overlong = new MockMultipartFile(
                "file", "a".repeat(252) + ".pdf", "application/pdf", "content".getBytes());

        assertThrows(FileStorageException.class,
                () -> FileValidationUtil.validateAndGetExtension(empty));
        assertThrows(FileStorageException.class,
                () -> FileValidationUtil.validateAndGetExtension(unsupported));
        assertThrows(FileStorageException.class,
                () -> FileValidationUtil.validateAndGetExtension(overlong));
    }

    @Test
    void rejectsUnsafeOriginalFileName() {
        MockMultipartFile traversalName = new MockMultipartFile(
                "file", "../document.pdf", "application/pdf", "content".getBytes());

        assertThrows(FileStorageException.class,
                () -> FileValidationUtil.validateAndGetExtension(traversalName));
    }
}
