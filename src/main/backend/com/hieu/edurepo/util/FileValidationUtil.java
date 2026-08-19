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

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new FileStorageException("Tên tệp không hợp lệ");
        }

        String extension = originalName.substring(originalName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("Chỉ chấp nhận tệp PDF, DOC hoặc DOCX");
        }
        return extension;
    }
}
