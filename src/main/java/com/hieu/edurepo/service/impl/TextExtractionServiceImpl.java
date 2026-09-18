package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.service.TextExtractionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.regex.Pattern;

@Service
public class TextExtractionServiceImpl implements TextExtractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextExtractionServiceImpl.class);

    private static final Pattern MULTI_WHITESPACE = Pattern.compile("[\\r\\t\\f ]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    @Override
    public String extractText(Path filePath) {
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            LOGGER.warn("File does not exist or is not a regular file: {}", filePath);
            return "";
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            return extractFromPdf(filePath.toFile());
        }

        LOGGER.info("Unsupported direct text extraction format for {}, skipping file content extraction", fileName);
        return "";
    }

    @Override
    public String extractText(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            return "";
        }

        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        if (lowerName.endsWith(".pdf")) {
            try {
                byte[] bytes = inputStream.readAllBytes();
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    String raw = stripper.getText(document);
                    return cleanText(raw);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to extract text from PDF stream {}: {}", fileName, e.getMessage());
                return "";
            }
        }
        return "";
    }

    private String extractFromPdf(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String raw = stripper.getText(document);
            return cleanText(raw);
        } catch (Exception e) {
            LOGGER.warn("Failed to extract text from PDF file {}: {}", file.getName(), e.getMessage());
            return "";
        }
    }

    @Override
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        // Chuẩn hóa Unicode NFC để tránh lỗi phân rã dấu tiếng Việt
        String normalized = Normalizer.normalize(rawText, Normalizer.Form.NFC);

        // Loại bỏ ký tự điều khiển ẩn không hợp lệ
        normalized = CONTROL_CHARS.matcher(normalized).replaceAll("");

        // Rút gọn khoảng trắng và ngắt dòng
        normalized = MULTI_WHITESPACE.matcher(normalized).replaceAll(" ");
        normalized = MULTI_NEWLINE.matcher(normalized).replaceAll("\n\n");

        return normalized.trim();
    }
}
