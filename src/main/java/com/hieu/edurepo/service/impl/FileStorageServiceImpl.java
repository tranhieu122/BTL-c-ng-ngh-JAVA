package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.util.FileValidationUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path uploadRoot;

    public FileStorageServiceImpl(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException exception) {
            throw new FileStorageException("Không thể tạo thư mục lưu tệp", exception);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String extension = FileValidationUtil.validateAndGetExtension(file);
        FileValidationUtil.validateContent(file, extension);
        String storedName = UUID.randomUUID() + "." + extension;
        Path destination = safeResolve(storedName);
        try (var input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            return storedName;
        } catch (IOException exception) {
            try { Files.deleteIfExists(destination); }
            catch (IOException cleanupException) { exception.addSuppressed(cleanupException); }
            throw new FileStorageException("Không thể lưu tệp tải lên", exception);
        }
    }

    @Override
    public Resource load(String storedFileName) {
        try {
            Resource resource = new UrlResource(safeResolve(storedFileName).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Không tìm thấy tệp: " + storedFileName);
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new FileStorageException("Đường dẫn tệp không hợp lệ", exception);
        }
    }

    @Override
    public String checksum(String storedFileName) {
        Path file = safeResolve(storedFileName);
        try (var input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new FileStorageException("Không thể tính mã kiểm tra của tệp", exception);
        }
    }

    @Override
    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(safeResolve(storedFileName));
        } catch (IOException exception) {
            throw new FileStorageException("Không thể xóa tệp", exception);
        }
    }

    private Path safeResolve(String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
                || fileName.indexOf('\u0000') >= 0) {
            throw new FileStorageException("Đường dẫn tệp không hợp lệ");
        }
        Path resolved = uploadRoot.resolve(fileName).normalize();
        if (!resolved.startsWith(uploadRoot) || !resolved.getParent().equals(uploadRoot)) {
            throw new FileStorageException("Đường dẫn tệp không hợp lệ");
        }
        return resolved;
    }
}
