package com.hieu.edurepo.util;

import com.hieu.edurepo.exception.FileStorageException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileValidationUtil {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final long DEFAULT_MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf", "application/octet-stream"),
            "doc", Set.of("application/msword", "application/octet-stream"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip", "application/octet-stream"));

    private FileValidationUtil() {
    }

    public static String validateAndGetExtension(MultipartFile file) {
        return validateAndGetExtension(file, DEFAULT_MAX_FILE_SIZE);
    }

    public static String validateAndGetExtension(MultipartFile file, long maxFileSize) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Tệp tải lên không được để trống");
        }
        if (maxFileSize <= 0 || file.getSize() > maxFileSize) {
            long maxMb = Math.max(1, maxFileSize / (1024 * 1024));
            throw new FileStorageException("Tệp tải lên vượt quá dung lượng tối đa " + maxMb + " MB");
        }

        String originalName = getSafeOriginalFileName(file);

        String extension = originalName.substring(originalName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("Chỉ chấp nhận tệp PDF, DOC hoặc DOCX");
        }
        validateDeclaredContentType(file, extension);
        return extension;
    }

    private static void validateDeclaredContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) return;
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.get(extension).contains(normalized)) {
            throw new FileStorageException("Loại nội dung tệp không khớp với phần mở rộng ." + extension);
        }
    }

    /** Check the container signature; this is not a full document parser. */
    public static void validateContent(MultipartFile file, String extension) {
        try (var input = new java.io.BufferedInputStream(file.getInputStream())) {
            input.mark(8);
            byte[] header = input.readNBytes(8);
            input.reset();
            boolean valid = switch (extension) {
                case "pdf" -> header.length >= 5 && new String(header, 0, 5,
                        java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
                case "doc" -> java.util.Arrays.equals(header,
                        new byte[]{(byte)0xd0, (byte)0xcf, 0x11, (byte)0xe0, (byte)0xa1, (byte)0xb1, 0x1a, (byte)0xe1});
                case "docx" -> isWordArchive(input);
                default -> false;
            };
            if (!valid) throw new FileStorageException("Nội dung tệp không khớp định dạng PDF, DOC hoặc DOCX đã chọn");
        } catch (java.io.IOException exception) {
            throw new FileStorageException("Không thể kiểm tra nội dung tệp", exception);
        }
    }

    private static boolean isWordArchive(java.io.InputStream input) throws java.io.IOException {
        boolean contentTypes = false;
        boolean document = false;
        long totalBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new java.util.zip.ZipInputStream(input)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 2048 || unsafeZipEntry(entry.getName())) return false;
                boolean hasContent = false;
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    totalBytes += read;
                    entryBytes += read;
                    hasContent |= read > 0;
                    if (entryBytes > 200L * 1024 * 1024 || totalBytes > 400L * 1024 * 1024) return false;
                }
                if (hasContent && entry.getName().equals("[Content_Types].xml")) contentTypes = true;
                if (hasContent && entry.getName().equals("word/document.xml")) document = true;
            }
        }
        return contentTypes && document;
    }

    private static boolean unsafeZipEntry(String name) {
        if (name == null || name.isBlank() || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                || name.indexOf('\u0000') >= 0
                || name.startsWith("/")) return true;
        return java.util.Arrays.stream(name.split("/", -1)).anyMatch(".."::equals);
    }

    public static String getSafeOriginalFileName(MultipartFile file) {
        if (file == null) {
            throw new FileStorageException("Tệp tải lên không hợp lệ");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()
                || originalName.indexOf('/') >= 0 || originalName.indexOf('\\') >= 0
                || originalName.indexOf(':') >= 0
                || originalName.indexOf('\u0000') >= 0
                || originalName.chars().anyMatch(FileValidationUtil::isUnsafeNameCharacter)
                || !originalName.equals(originalName.strip())) {
            throw new FileStorageException("Tên tệp không an toàn hoặc không hợp lệ");
        }
        if (originalName.length() > 255) {
            throw new FileStorageException("Tên tệp không được vượt quá 255 ký tự");
        }
        if (!originalName.contains(".") || originalName.endsWith(".")) {
            throw new FileStorageException("Tên tệp không hợp lệ");
        }
        String baseName = originalName.substring(0, originalName.indexOf('.')).toUpperCase(Locale.ROOT);
        if (baseName.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new FileStorageException("Tên tệp không an toàn hoặc không hợp lệ");
        }
        return originalName;
    }

    private static boolean isUnsafeNameCharacter(int character) {
        return Character.isISOControl(character)
                || character == 0x202A || character == 0x202B || character == 0x202D
                || character == 0x202E || character == 0x202C
                || character == 0x2066 || character == 0x2067 || character == 0x2068
                || character == 0x2069;
    }
}
