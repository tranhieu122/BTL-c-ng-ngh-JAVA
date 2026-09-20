package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.dto.DocumentSection;
import com.hieu.edurepo.dto.ExtractedPage;
import com.hieu.edurepo.service.SectionDetectionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation dịch vụ nhận diện phân cấp Section / Subsection / Heading cho tài liệu.
 */
@Service
public class SectionDetectionServiceImpl implements SectionDetectionService {

    /** Mẫu chương/phần: "Chương 1: Tổng quan", "Chapter 3 - Spring Security", "Phần I. Mở đầu" */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^(?:Chương|CHƯƠNG|Chapter|CHAPTER|Phần|PHẦN|Mục|MỤC|Bài|BÀI)\\s+([0-9IVXLCDM]+|[A-Z])(?:\\s*[:.\\-–]\\s*|\\s+)(.{2,150})$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /** Mẫu tiêu đề mục chính: "1. Giới thiệu", "3. Spring Security", "I. Tổng quan", "A. Mở đầu" */
    private static final Pattern MAIN_SECTION_PATTERN = Pattern.compile(
            "^(?:([0-9]+|[IVXLCDM]+|[A-Z])\\.\\s+|#\\s+)([A-Z\u00C0-\u1EF90-9].{2,150})$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /** Mẫu tiêu đề mục con cấp 2: "1.1 Bối cảnh", "3.1 Authentication", "## Authentication" */
    private static final Pattern SUB_SECTION_PATTERN = Pattern.compile(
            "^(?:([0-9]+\\.[0-9]+)\\.?\\s+|##\\s+)([A-Z\u00C0-\u1EF90-9].{2,150})$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /** Mẫu tiêu đề mục con cấp 3: "1.1.1 Chi tiết", "3.1.2 Form Login", "### Form Login" */
    private static final Pattern SUB_SUB_SECTION_PATTERN = Pattern.compile(
            "^(?:([0-9]+\\.[0-9]+\\.[0-9]+)\\.?\\s+|###\\s+)([A-Z\u00C0-\u1EF90-9].{2,150})$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    @Override
    public List<DocumentSection> detectSections(List<ExtractedPage> pages, String defaultTitle) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        List<DocumentSection> sections = new ArrayList<>();
        String currentSection = defaultTitle != null && !defaultTitle.isBlank() ? defaultTitle.trim() : "Nội dung chính";
        String currentSubsection = null;
        StringBuilder contentAccumulator = new StringBuilder();
        int sectionStartPage = pages.get(0).getPageNumber();
        int lastContentPage = sectionStartPage;
        int currentEndPage = sectionStartPage;

        for (ExtractedPage page : pages) {
            int pageNum = page.getPageNumber();
            currentEndPage = pageNum;
            String text = page.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    contentAccumulator.append("\n");
                    continue;
                }

                // 1. Kiểm tra Chương/Phần hoặc Mục chính
                Matcher chapterMatcher = CHAPTER_PATTERN.matcher(trimmed);
                Matcher mainMatcher = MAIN_SECTION_PATTERN.matcher(trimmed);
                if (chapterMatcher.matches() || (mainMatcher.matches() && !isFalsePositiveHeading(trimmed))) {
                    // Đóng section trước đó nếu đã có nội dung
                    if (contentAccumulator.length() > 0 && !contentAccumulator.toString().isBlank()) {
                        sections.add(new DocumentSection(
                                currentSection,
                                currentSubsection,
                                contentAccumulator.toString().trim(),
                                sectionStartPage,
                                lastContentPage
                        ));
                        contentAccumulator.setLength(0);
                    }
                    currentSection = trimmed;
                    currentSubsection = null;
                    sectionStartPage = pageNum;
                    lastContentPage = pageNum;
                    continue;
                }

                // 2. Kiểm tra Mục con cấp 3 (Sub-sub-section)
                Matcher subSubMatcher = SUB_SUB_SECTION_PATTERN.matcher(trimmed);
                if (subSubMatcher.matches() && !isFalsePositiveHeading(trimmed)) {
                    if (contentAccumulator.length() > 0 && !contentAccumulator.toString().isBlank()) {
                        sections.add(new DocumentSection(
                                currentSection,
                                currentSubsection,
                                contentAccumulator.toString().trim(),
                                sectionStartPage,
                                lastContentPage
                        ));
                        contentAccumulator.setLength(0);
                    }
                    currentSubsection = trimmed;
                    sectionStartPage = pageNum;
                    lastContentPage = pageNum;
                    continue;
                }

                // 3. Kiểm tra Mục con cấp 2 (Subsection)
                Matcher subMatcher = SUB_SECTION_PATTERN.matcher(trimmed);
                if (subMatcher.matches() && !isFalsePositiveHeading(trimmed)) {
                    if (contentAccumulator.length() > 0 && !contentAccumulator.toString().isBlank()) {
                        sections.add(new DocumentSection(
                                currentSection,
                                currentSubsection,
                                contentAccumulator.toString().trim(),
                                sectionStartPage,
                                lastContentPage
                        ));
                        contentAccumulator.setLength(0);
                    }
                    currentSubsection = trimmed;
                    sectionStartPage = pageNum;
                    lastContentPage = pageNum;
                    continue;
                }

                // Nội dung thông thường của section
                contentAccumulator.append(line).append("\n");
                lastContentPage = pageNum;
            }
        }

        // Đóng section cuối cùng
        if (contentAccumulator.length() > 0 && !contentAccumulator.toString().isBlank()) {
            sections.add(new DocumentSection(
                    currentSection,
                    currentSubsection,
                    contentAccumulator.toString().trim(),
                    sectionStartPage,
                    lastContentPage
            ));
        }

        return sections;
    }

    /**
     * Loại trừ các trường hợp ngẫu nhiên giống số thứ tự nhưng thực chất là câu văn (false positives),
     * ví dụ "1. Đây là một câu dài miêu tả quy trình hệ thống bắt đầu bằng dấu chấm..."
     */
    private boolean isFalsePositiveHeading(String line) {
        // Nếu dòng quá dài (> 120 ký tự) và kết thúc bằng dấu chấm kết câu, thường là một câu văn bình thường
        if (line.length() > 120 && line.endsWith(".")) {
            return true;
        }
        // Nếu có nhiều hơn 2 dấu chấm rải rác trong câu
        long periodCount = line.chars().filter(ch -> ch == '.').count();
        return periodCount > 3;
    }
}
