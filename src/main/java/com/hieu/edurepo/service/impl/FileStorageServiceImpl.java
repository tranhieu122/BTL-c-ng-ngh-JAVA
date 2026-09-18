package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.observability.OperationalMetrics;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.FileThreatScanner;
import com.hieu.edurepo.util.FileValidationUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private final Path uploadRoot;
    private final long maxFileSize;
    private final FileThreatScanner threatScanner;
    private final OperationalMetrics metrics;

    public FileStorageServiceImpl(String uploadDir) {
        this(uploadDir, 200L * 1024 * 1024, (file, originalName) -> true,
                new OperationalMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    public FileStorageServiceImpl(@Value("${app.upload.dir:uploads}") String uploadDir,
                                  @Value("${app.upload.max-file-size-bytes:209715200}") long maxFileSize,
                                  FileThreatScanner threatScanner) {
        this(uploadDir, maxFileSize, threatScanner,
                new OperationalMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Autowired
    public FileStorageServiceImpl(@Value("${app.upload.dir:uploads}") String uploadDir,
                                  @Value("${app.upload.max-file-size-bytes:209715200}") long maxFileSize,
                                  FileThreatScanner threatScanner,
                                  OperationalMetrics metrics) {
        if (uploadDir == null || uploadDir.isBlank()) {
            throw new IllegalStateException("Upload directory must be configured");
        }
        // Chuẩn hóa thư mục upload ngay từ đầu để các phép kiểm tra path phía dưới luôn dùng đường dẫn tuyệt đối.
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (maxFileSize <= 0) throw new IllegalArgumentException("Dung lượng upload tối đa phải lớn hơn 0");
        this.maxFileSize = maxFileSize;
        this.threatScanner = threatScanner;
        this.metrics = metrics;
    }

    @PostConstruct
    public void initialize() {
        try {
            // Tạo thư mục lưu file khi ứng dụng khởi động; nếu không tạo được thì dừng sớm thay vì lỗi lúc upload.
            Files.createDirectories(uploadRoot);
            cleanupInterruptedUploads();
        } catch (IOException exception) {
            throw new FileStorageException("Không thể tạo thư mục lưu tệp", exception);
        }
    }

    private void cleanupInterruptedUploads() throws IOException {
        try (var files = Files.list(uploadRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(".upload-")
                            && path.getFileName().toString().endsWith(".tmp"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            LOGGER.warn("Could not remove an interrupted upload staging file (exception={})",
                                    exception.getClass().getSimpleName());
                            LOGGER.debug("Interrupted upload cleanup diagnostic", exception);
                        }
                    });
        }
    }

    @Override
    public String store(MultipartFile file) {
        // Kiểm tra cả đuôi file và chữ ký nội dung để tránh đổi tên file độc hại thành .pdf/.docx.
        long validationStarted = System.nanoTime();
        String extension;
        try {
            extension = FileValidationUtil.validateAndGetExtension(file, maxFileSize);
            FileValidationUtil.validateContent(file, extension);
        } catch (FileStorageException exception) {
            metrics.uploadRejected(classifyValidationFailure(exception));
            throw exception;
        } finally {
            metrics.uploadValidation(elapsed(validationStarted));
        }
        // Tên file lưu trên server dùng UUID để không lộ tên gốc và không bị trùng giữa nhiều lần upload.
        String storedName = UUID.randomUUID() + "." + extension;
        Path destination = safeResolve(storedName);
        Path staged = safeResolve(".upload-" + UUID.randomUUID() + ".tmp");
        try {
            copyWithLimit(file, staged);
            long scanStarted = System.nanoTime();
            boolean safe;
            try {
                safe = threatScanner.isSafe(staged, FileValidationUtil.getSafeOriginalFileName(file));
                metrics.scanner(elapsed(scanStarted), safe ? "SAFE" : "REJECTED");
            } catch (IOException | RuntimeException scannerFailure) {
                metrics.scanner(elapsed(scanStarted), "ERROR");
                throw scannerFailure;
            }
            if (!safe) {
                metrics.uploadRejected(OperationalMetrics.UploadFailureReason.SCANNER_REJECTED);
                throw new FileStorageException("Tệp tải lên không vượt qua kiểm tra an toàn");
            }
            moveIntoPlace(staged, destination);
            metrics.uploadSucceeded(file.getSize());
            return storedName;
        } catch (FileStorageException exception) {
            cleanup(staged, destination, exception);
            if (!exception.getMessage().contains("kiểm tra an toàn")) {
                OperationalMetrics.UploadFailureReason reason = classifyValidationFailure(exception);
                metrics.uploadRejected(reason);
                if (reason == OperationalMetrics.UploadFailureReason.STORAGE_ERROR) metrics.uploadStorageError();
            }
            throw exception;
        } catch (IOException exception) {
            cleanup(staged, destination, exception);
            metrics.uploadRejected(OperationalMetrics.UploadFailureReason.STORAGE_ERROR);
            metrics.uploadStorageError();
            throw new FileStorageException("Không thể lưu tệp tải lên", exception);
        } catch (RuntimeException exception) {
            cleanup(staged, destination, exception);
            metrics.uploadRejected(OperationalMetrics.UploadFailureReason.STORAGE_ERROR);
            metrics.uploadStorageError();
            throw exception;
        }
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private OperationalMetrics.UploadFailureReason classifyValidationFailure(FileStorageException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("để trống")) return OperationalMetrics.UploadFailureReason.EMPTY;
        if (message.contains("dung lượng")) return OperationalMetrics.UploadFailureReason.TOO_LARGE;
        if (message.contains("PDF, DOC hoặc DOCX")) return OperationalMetrics.UploadFailureReason.UNSUPPORTED_EXTENSION;
        if (message.contains("Loại nội dung")) return OperationalMetrics.UploadFailureReason.MIME_MISMATCH;
        if (message.contains("Nội dung tệp")) return OperationalMetrics.UploadFailureReason.INVALID_SIGNATURE;
        if (message.contains("Tên tệp") || message.contains("Đường dẫn")) {
            return OperationalMetrics.UploadFailureReason.UNSAFE_FILENAME;
        }
        if (message.contains("archive") || message.contains("ZIP") || message.contains("nén")) {
            return OperationalMetrics.UploadFailureReason.UNSAFE_ARCHIVE;
        }
        return OperationalMetrics.UploadFailureReason.STORAGE_ERROR;
    }

    private void copyWithLimit(MultipartFile file, Path staged) throws IOException {
        try (var input = file.getInputStream(); var output = Files.newOutputStream(staged)) {
            byte[] buffer = new byte[8192];
            long copied = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > maxFileSize) {
                    throw new FileStorageException("Tệp tải lên vượt quá dung lượng tối đa");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private void moveIntoPlace(Path staged, Path destination) throws IOException {
        try {
            Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(staged, destination);
        }
    }

    private void cleanup(Path staged, Path destination, Exception original) {
        try { Files.deleteIfExists(staged); }
        catch (IOException cleanupException) { original.addSuppressed(cleanupException); }
        try { Files.deleteIfExists(destination); }
        catch (IOException cleanupException) { original.addSuppressed(cleanupException); }
    }

    @Override
    public Resource load(String storedFileName) {
        try {
            Resource resource = new UrlResource(safeResolve(storedFileName).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Không tìm thấy tệp được yêu cầu");
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
            // SHA-256 dùng để nhận diện chính xác nội dung từng phiên bản file, kể cả khi tên file giống nhau.
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
        // Chỉ chấp nhận tên file đơn, không nhận đường dẫn con như ../secret hoặc uploads/a.pdf.
        if (fileName == null || fileName.isBlank()
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
                || fileName.indexOf('\u0000') >= 0) {
            throw new FileStorageException("Đường dẫn tệp không hợp lệ");
        }
        Path resolved = uploadRoot.resolve(fileName).normalize();
        // Chặn path traversal: file sau khi resolve phải nằm trực tiếp trong uploadRoot.
        if (!resolved.startsWith(uploadRoot) || !resolved.getParent().equals(uploadRoot)) {
            throw new FileStorageException("Đường dẫn tệp không hợp lệ");
        }
        return resolved;
    }
}
