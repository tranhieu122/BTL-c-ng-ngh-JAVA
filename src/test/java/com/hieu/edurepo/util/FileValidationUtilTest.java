package com.hieu.edurepo.util;

import com.hieu.edurepo.exception.FileStorageException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kiểm thử bộ tiện ích kiểm định tệp tin tải lên (File Validation Util Test).
 * Kiểm tra xác thực định dạng tài liệu hợp lệ, kiểm tra kích thước tối đa và phát hiện tệp giả mạo phần mở rộng.
 */
class FileValidationUtilTest {

    @Test
    void rejectsTextDisguisedAsPdfOrDoc() {
        for (String extension : new String[]{"pdf", "doc", "docx"}) {
            var file = new MockMultipartFile("file", "fake." + extension, "application/octet-stream", "not a document".getBytes());
            assertThrows(FileStorageException.class, () -> FileValidationUtil.validateContent(file, extension));
        }
    }

    @Test
    void recognizesPdfAndOleContainerSignatures() {
        var pdf = new MockMultipartFile("file", "valid.pdf", "application/pdf", "%PDF-1.7 content".getBytes());
        var doc = new MockMultipartFile("file", "valid.doc", "application/msword",
                new byte[]{(byte)0xd0, (byte)0xcf, 0x11, (byte)0xe0, (byte)0xa1, (byte)0xb1, 0x1a, (byte)0xe1});
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> FileValidationUtil.validateContent(pdf, "pdf"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> FileValidationUtil.validateContent(doc, "doc"));
    }

    @Test
    void wordArchiveRequiresWordDocumentAndContentTypes() throws Exception {
        var valid = wordArchive("[Content_Types].xml", "word/document.xml");
        var otherZip = wordArchive("[Content_Types].xml", "xl/workbook.xml");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> FileValidationUtil.validateContent(valid, "docx"));
        assertThrows(FileStorageException.class, () -> FileValidationUtil.validateContent(otherZip, "docx"));
    }

    @Test
    void rejectsOversizedMultipartEvenOutsideHttpController() {
        var oversized = new MockMultipartFile("file", "large.pdf", "application/pdf", "%PDF-1.7".getBytes()) {
            @Override public long getSize() { return 200L * 1024 * 1024 + 1; }
        };
        assertThrows(FileStorageException.class, () -> FileValidationUtil.validateAndGetExtension(oversized));
    }

    private MockMultipartFile wordArchive(String... names) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            for (String name : names) {
                zip.putNextEntry(new java.util.zip.ZipEntry(name));
                zip.write("<xml/>".getBytes()); zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "document.docx", "application/octet-stream", bytes.toByteArray());
    }

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

    @Test
    void rejectsMismatchedMimeAndDangerousWindowsOrBidiNames() {
        var wrongMime = new MockMultipartFile("file", "document.pdf", "image/png", "%PDF-1.7".getBytes());
        var reserved = new MockMultipartFile("file", "CON.pdf", "application/pdf", "%PDF-1.7".getBytes());
        var bidi = new MockMultipartFile("file", "safe\u202Efdp.exe.pdf", "application/pdf", "%PDF-1.7".getBytes());

        assertThrows(FileStorageException.class, () -> FileValidationUtil.validateAndGetExtension(wrongMime));
        assertThrows(FileStorageException.class, () -> FileValidationUtil.validateAndGetExtension(reserved));
        assertThrows(FileStorageException.class, () -> FileValidationUtil.validateAndGetExtension(bidi));
    }

    @Test
    void rejectsDocxWithTraversalEntry() throws Exception {
        var suspicious = wordArchive("[Content_Types].xml", "word/document.xml", "../payload.exe");
        assertThrows(FileStorageException.class, () -> FileValidationUtil.validateContent(suspicious, "docx"));
    }
}
