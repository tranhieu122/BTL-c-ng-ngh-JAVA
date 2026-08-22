package com.hieu.edurepo.util;

import com.hieu.edurepo.exception.FileStorageException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

public final class FileValidationUtil {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private FileValidationUtil() {
    }

    public static String validateAndGetExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Tệp tải lên không được để trống");
        }

        String originalName = getSafeOriginalFileName(file);

        String extension = originalName.substring(originalName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("Chỉ chấp nhận tệp PDF, DOC hoặc DOCX");
        }
        return extension;
    }

    public static String getSafeOriginalFileName(MultipartFile file) {
        if (file == null) {
            throw new FileStorageException("Tệp tải lên không hợp lệ");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()
                || originalName.indexOf('/') >= 0 || originalName.indexOf('\\') >= 0
                || originalName.indexOf('\u0000') >= 0
                || originalName.chars().anyMatch(Character::isISOControl)) {
            throw new FileStorageException("Tên tệp không an toàn hoặc không hợp lệ");
        }
        if (originalName.length() > 255) {
            throw new FileStorageException("Tên tệp không được vượt quá 255 ký tự");
        }
        if (!originalName.contains(".") || originalName.endsWith(".")) {
            throw new FileStorageException("Tên tệp không hợp lệ");
        }
        return originalName;
    }
}
