package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ExtractedPage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Dịch vụ trích xuất văn bản từ tài liệu PDF/file tải lên, hỗ trợ tách trang và phát hiện trang scan/OCR.
 */
public interface TextExtractionService {

    /**
     * Trích xuất văn bản đầy đủ từ đường dẫn file trên đĩa.
     */
    String extractText(Path filePath);

    /**
     * Trích xuất văn bản từ luồng InputStream kèm tên file.
     */
    String extractText(InputStream inputStream, String fileName);

    /**
     * Trích xuất danh sách các trang tài liệu có cấu trúc (ExtractedPage),
     * tự động phân định trang có Text Layer hay trang Scan để kích hoạt OCR tương ứng.
     */
    List<ExtractedPage> extractPages(InputStream inputStream, String fileName);

    /**
     * Trích xuất danh sách các trang từ file trên đĩa.
     */
    List<ExtractedPage> extractPages(Path filePath);

    /**
     * Làm sạch văn bản thô.
     */
    String cleanText(String rawText);
}
