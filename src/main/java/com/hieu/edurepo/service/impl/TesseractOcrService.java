package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.service.OcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Implementation dịch vụ OCR sử dụng Tesseract engine (thông qua CLI/Process hoặc cấu hình môi trường).
 * Có cơ chế tự động phát hiện (Self-detection) và fallback an toàn nếu máy chủ chưa cài đặt Tesseract.
 */
@Service
public class TesseractOcrService implements OcrService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TesseractOcrService.class);

    private final RagProperties ragProperties;
    private final boolean available;
    private final String executablePath;
    private final String statusDescription;

    public TesseractOcrService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;

        DetectionResult detection = detectTesseract(ragProperties);
        this.available = detection.available;
        this.executablePath = detection.executablePath;
        this.statusDescription = detection.description;

        if (this.available) {
            LOGGER.info("Tesseract OCR detected successfully at '{}'. OCR is ACTIVE for scanned documents.", executablePath);
        } else {
            LOGGER.info("Tesseract OCR is NOT active: {}. System will fallback to text-layer extraction.", statusDescription);
        }
    }

    @Override
    public String extractText(byte[] imageBytes) {
        if (!isAvailable() || imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            return extractText(image);
        } catch (Exception e) {
            LOGGER.warn("Failed to read image bytes for OCR: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public String extractText(BufferedImage image) {
        if (!isAvailable() || image == null) {
            return "";
        }

        Path tempImage = null;
        Path tempOutputBase = null;
        try {
            tempImage = Files.createTempFile("edurepo_ocr_in_", ".png");
            tempOutputBase = Files.createTempFile("edurepo_ocr_out_", "");

            // Ghi ảnh tạm thời dạng PNG
            ImageIO.write(image, "png", tempImage.toFile());

            String lang = ragProperties.getOcrLanguage();
            if (lang == null || lang.isBlank()) {
                lang = "vie+eng";
            }

            ProcessBuilder pb = new ProcessBuilder(
                    executablePath,
                    tempImage.toAbsolutePath().toString(),
                    tempOutputBase.toAbsolutePath().toString(),
                    "-l", lang
            );

            String tessDataPath = ragProperties.getTesseractDataPath();
            if (tessDataPath != null && !tessDataPath.isBlank()) {
                pb.environment().put("TESSDATA_PREFIX", tessDataPath);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                LOGGER.warn("Tesseract OCR process timed out after 45s");
                return "";
            }

            // Tesseract tự động thêm đuôi .txt vào file output
            Path resultTxtFile = Path.of(tempOutputBase.toAbsolutePath() + ".txt");
            if (Files.exists(resultTxtFile)) {
                String ocrResult = Files.readString(resultTxtFile);
                Files.deleteIfExists(resultTxtFile);
                return ocrResult != null ? ocrResult.trim() : "";
            }

            return "";
        } catch (Exception e) {
            LOGGER.warn("Error during Tesseract OCR extraction: {}", e.getMessage());
            return "";
        } finally {
            if (tempImage != null) {
                try {
                    Files.deleteIfExists(tempImage);
                } catch (Exception ignored) {
                }
            }
            if (tempOutputBase != null) {
                try {
                    Files.deleteIfExists(tempOutputBase);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return ragProperties.isOcrEnabled() && available;
    }

    @Override
    public String getStatusDescription() {
        return statusDescription;
    }

    private static DetectionResult detectTesseract(RagProperties properties) {
        if (!properties.isOcrEnabled()) {
            return new DetectionResult(false, null, "OCR is disabled by configuration (rag.ingestion.ocr-enabled=false)");
        }

        // Danh sách các đường dẫn ứng viên phổ biến
        String[] candidatePaths = new String[]{
                "tesseract",
                "tesseract.exe",
                "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe",
                "/usr/bin/tesseract",
                "/usr/local/bin/tesseract"
        };

        for (String candidate : candidatePaths) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                Process process = pb.start();
                boolean completed = process.waitFor(3, TimeUnit.SECONDS);
                if (completed && process.exitValue() == 0) {
                    return new DetectionResult(true, candidate, "Tesseract engine ready at " + candidate);
                }
            } catch (Exception ignored) {
                // Tiếp tục thử ứng viên tiếp theo
            }
        }

        return new DetectionResult(
                false,
                null,
                "Tesseract binary not found in PATH or standard installation folders. Scanned PDF fallback will proceed with zero-text detection warning."
        );
    }

    private static class DetectionResult {
        final boolean available;
        final String executablePath;
        final String description;

        DetectionResult(boolean available, String executablePath, String description) {
            this.available = available;
            this.executablePath = executablePath;
            this.description = description;
        }
    }
}
