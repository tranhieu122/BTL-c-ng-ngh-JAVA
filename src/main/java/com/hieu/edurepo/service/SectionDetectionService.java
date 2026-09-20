package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentSection;
import com.hieu.edurepo.dto.ExtractedPage;

import java.util.List;

/**
 * Dịch vụ nhận diện phân đoạn tài liệu (Section / Subsection / Heading Detection) dựa trên quy tắc (Rule-based & Regex).
 * Giúp chia tài liệu theo cấu trúc phân cấp (Hierarchical Structure) thay vì chỉ chia phẳng theo độ dài ký tự.
 */
public interface SectionDetectionService {

    /**
     * Phân tích các trang tài liệu đã được làm sạch và phát hiện các Section / Subsection cùng khoảng trang (page range).
     *
     * @param pages Danh sách trang đã trích xuất và làm sạch
     * @param defaultTitle Tiêu đề mặc định nếu tài liệu không có heading rõ ràng
     * @return Danh sách các DocumentSection có thứ tự
     */
    List<DocumentSection> detectSections(List<ExtractedPage> pages, String defaultTitle);
}
