package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.ExtractedPage;
import com.hieu.edurepo.enums.PageSourceType;
import com.hieu.edurepo.service.OcrService;
import com.hieu.edurepo.service.TextCleaningService;
import com.hieu.edurepo.service.TextExtractionService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation trích xuất văn bản tài liệu bằng Apache PDFBox kết hợp phát hiện trang scan và OCR thông minh.
 */
@Service
public class TextExtractionServiceImpl implements TextExtractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextExtractionServiceImpl.class);

    private final RagProperties ragProperties;
    private final OcrService ocrService;
    private final TextCleaningService textCleaningService;

    public TextExtractionServiceImpl(RagProperties ragProperties,
                                    OcrService ocrService,
                                    TextCleaningService textCleaningService) {
        this.ragProperties = ragProperties;
        this.ocrService = ocrService;
        this.textCleaningService = textCleaningService;
    }

    private static final int MAX_OCR_PAGES_PER_DOCUMENT = 50;

    @Override
    public List<ExtractedPage> extractPages(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            return List.of();
        }

        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        if (!lowerName.endsWith(".pdf")) {
            LOGGER.info("File '{}' is not a PDF, extracting raw content if text...", fileName);
            try {
                String content = new String(inputStream.readAllBytes());
                return List.of(new ExtractedPage(1, content, PageSourceType.TEXT_LAYER, false));
            } catch (Exception e) {
                LOGGER.warn("Failed to read stream for non-pdf file {}: {}", fileName, e.getMessage());
                return List.of();
            }
        }

        Path tempPdfPath = null;
        try {
            // Ghi luồng ra tệp tạm để PDFBox nạp phân trang theo đĩa (disk-backed), tránh ngốn heap array
            tempPdfPath = Files.createTempFile("edurepo_pdf_stream_", ".pdf");
            Files.copy(inputStream, tempPdfPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (PDDocument document = Loader.loadPDF(tempPdfPath.toFile())) {
                return processPdfPages(document, fileName);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract pages from PDF stream {}: {}", fileName, e.getMessage());
            return List.of();
        } finally {
            if (tempPdfPath != null) {
                try {
                    Files.deleteIfExists(tempPdfPath);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public List<ExtractedPage> extractPages(Path filePath) {
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            LOGGER.warn("File does not exist or is not a regular file: {}", filePath);
            return List.of();
        }

        String fileName = filePath.getFileName().toString();
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return processPdfPages(document, fileName);
        } catch (Exception e) {
            LOGGER.warn("Failed to extract pages from PDF file {}: {}", fileName, e.getMessage());
            return List.of();
        }
    }

    private List<ExtractedPage> processPdfPages(PDDocument document, String fileName) throws java.io.IOException {
        int totalPages = document.getNumberOfPages();
        int minTextLength = ragProperties.getMinPageTextLength();

        List<ExtractedPage> pages = new ArrayList<>(totalPages);
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);

        PDFRenderer renderer = null;

        int textLayerPagesCount = 0;
        int ocrPagesCount = 0;

        for (int page = 1; page <= totalPages; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);

            String extractedText = stripper.getText(document);
            int charCount = (extractedText != null) ? extractedText.strip().length() : 0;

            // Kiểm tra xem trang có text layer đầy đủ hay không
            if (charCount >= minTextLength) {
                pages.add(new ExtractedPage(page, extractedText, PageSourceType.TEXT_LAYER, false));
                textLayerPagesCount++;
            } else {
                // Trang có ít hoặc không có text layer: Xác định là trang Scan/Image
                boolean isScanned = true;
                if (ragProperties.isOcrEnabled() && ocrService.isAvailable()) {
                    if (ocrPagesCount >= MAX_OCR_PAGES_PER_DOCUMENT) {
                        LOGGER.warn("Reached maximum OCR limit ({} pages) for '{}'. Skipping OCR for page {}.",
                                MAX_OCR_PAGES_PER_DOCUMENT, fileName, page);
                        pages.add(new ExtractedPage(page, extractedText != null ? extractedText : "", PageSourceType.TEXT_LAYER, isScanned));
                        continue;
                    }

                    BufferedImage pageImage = null;
                    try {
                        if (renderer == null) {
                            renderer = new PDFRenderer(document);
                        }
                        // Render ảnh ở độ phân giải 200 DPI phù hợp cho OCR
                        pageImage = renderer.renderImageWithDPI(page - 1, 200);
                        String ocrResult = ocrService.extractText(pageImage);

                        if (ocrResult != null && !ocrResult.isBlank()) {
                            pages.add(new ExtractedPage(page, ocrResult, PageSourceType.OCR, true));
                            ocrPagesCount++;
                            continue;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("OCR failed on page {} of {}: {}", page, fileName, e.getMessage());
                    } finally {
                        if (pageImage != null) {
                            pageImage.flush(); // Thu hồi bộ nhớ bitmap native ngay lập tức
                        }
                    }
                } else if (ragProperties.isOcrEnabled() && !ocrService.isAvailable()) {
                    LOGGER.debug("Page {} of {} has low text ({} chars) but OCR engine is unavailable: {}",
                            page, fileName, charCount, ocrService.getStatusDescription());
                }

                // Fallback nếu không có OCR hoặc OCR trả về rỗng: giữ lại bất kỳ text nào đã có
                pages.add(new ExtractedPage(page, extractedText != null ? extractedText : "", PageSourceType.TEXT_LAYER, isScanned));
            }
        }

        LOGGER.info("PDF Ingestion stats for '{}' - Total pages: {}, Text-layer pages: {}, OCR-processed pages: {}",
                fileName, totalPages, textLayerPagesCount, ocrPagesCount);

        return pages;
    }

    @Override
    public String extractText(Path filePath) {
        List<ExtractedPage> pages = extractPages(filePath);
        return formatPagesToString(pages);
    }

    @Override
    public String extractText(InputStream inputStream, String fileName) {
        List<ExtractedPage> pages = extractPages(inputStream, fileName);
        return formatPagesToString(pages);
    }

    private String formatPagesToString(List<ExtractedPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ExtractedPage page : pages) {
            String text = cleanText(page.getText());
            if (!text.isBlank()) {
                sb.append("\n\n[[EDUREPO_PAGE:").append(page.getPageNumber()).append("]]\n").append(text);
            }
        }
        return sb.toString().trim();
    }

    @Override
    public String cleanText(String rawText) {
        return textCleaningService.cleanText(rawText);
    }
}
