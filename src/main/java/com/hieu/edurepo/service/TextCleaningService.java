package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ExtractedPage;

import java.util.List;

/**
 * Dịch vụ tiền xử lý và làm sạch văn bản (Text Cleaning & Normalization) cho Document Ingestion Pipeline.
 * Đảm bảo bảo toàn các thuật ngữ công nghệ, khối mã lệnh (code), bảng biểu và chữ tiếng Việt Unicode.
 */
public interface TextCleaningService {

    /**
     * Làm sạch một đoạn văn bản thô:
     * - Chuẩn hóa Unicode NFC
     * - Loại bỏ ký tự điều khiển (control chars)
     * - Nối từ bị gạch ngang ngắt dòng (Secu-\nrity -> Security)
     * - Giữ nguyên các từ ghép (machine-learning) và định dạng code/indentation.
     */
    String cleanText(String rawText);

    /**
     * Xử lý và làm sạch danh sách các trang tài liệu:
     * - Tự động phát hiện và loại bỏ header/footer lặp lại qua nhiều trang
     * - Áp dụng làm sạch nội dung từng trang mà vẫn giữ nguyên page number
     */
    List<ExtractedPage> cleanPages(List<ExtractedPage> pages);

    /**
     * Nối an toàn các từ bị đứt gãy do ngắt dòng kèm dấu gạch ngang (hyphenation).
     */
    String repairHyphenation(String text);
}
