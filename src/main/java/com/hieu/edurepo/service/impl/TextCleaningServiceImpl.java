package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.ExtractedPage;
import com.hieu.edurepo.service.TextCleaningService;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation dịch vụ làm sạch văn bản tối ưu cho RAG ingestion.
 */
@Service
public class TextCleaningServiceImpl implements TextCleaningService {

    /** Ký tự điều khiển vô hình (ngoại trừ \t, \n, \r) */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /** Dấu gạch nối bị ngắt dòng: từ 2 ký tự trở lên + dấu '-' + xuống dòng + từ 2 ký tự tiếp theo */
    private static final Pattern LINEBREAK_HYPHEN = Pattern.compile("([a-zA-Z\u00C0-\u1EF9]{2,})-\\s*\\r?\\n\\s*([a-zA-Z\u00C0-\u1EF9]{2,})");

    /** Các mẫu số trang độc lập ở đầu/cuối trang */
    private static final Pattern STANDALONE_PAGE_NUM = Pattern.compile("^(?:Trang|Page)?\\s*\\d+(?:\\s*[/|–-]\\s*\\d+)?\\.?$", Pattern.CASE_INSENSITIVE);

    /** Dòng trống liên tiếp từ 3 dòng trở lên */
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}");

    private final RagProperties ragProperties;

    public TextCleaningServiceImpl(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    @Override
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        // 1. Chuẩn hóa Unicode sang dạng NFC để bảo toàn dấu tiếng Việt thống nhất
        String text = Normalizer.normalize(rawText, Normalizer.Form.NFC);

        // 2. Loại bỏ các ký tự điều khiển ẩn
        text = CONTROL_CHARS.matcher(text).replaceAll("");

        // 3. Nối từ bị đứt gãy do xuống dòng kèm dấu gạch ngang (nếu cấu hình bật)
        if (ragProperties.isCleanHyphenation()) {
            text = repairHyphenation(text);
        }

        // 4. Chuẩn hóa dòng và khoảng trắng, nhưng tôn trọng thụt dòng của code và bảng biểu
        text = normalizeLinesAndWhitespace(text, ragProperties.isPreserveCodeBlocks());

        return text.trim();
    }

    @Override
    public List<ExtractedPage> cleanPages(List<ExtractedPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        // Phát hiện các header và footer lặp lại trên nhiều trang
        Set<String> repeatedHeadersFooters = detectRepeatedHeaderFooters(pages);

        List<ExtractedPage> cleanedList = new ArrayList<>(pages.size());
        for (ExtractedPage page : pages) {
            String pageText = page.getText();
            if (pageText == null || pageText.isBlank()) {
                cleanedList.add(page);
                continue;
            }

            // Loại bỏ header/footer đã phát hiện
            String strippedText = stripHeaderFooters(pageText, repeatedHeadersFooters);

            // Làm sạch toàn diện
            String cleanedContent = cleanText(strippedText);

            cleanedList.add(new ExtractedPage(
                    page.getPageNumber(),
                    cleanedContent,
                    page.getSourceType(),
                    page.isScanned()
            ));
        }

        return cleanedList;
    }

    @Override
    public String repairHyphenation(String text) {
        if (text == null || text.isBlank() || !text.contains("-")) {
            return text != null ? text : "";
        }

        Matcher matcher = LINEBREAK_HYPHEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String part1 = matcher.group(1);
            String part2 = matcher.group(2);
            // Nối liền hai nửa từ lại: e.g. "Secu-" + "\n" + "rity" -> "Security"
            matcher.appendReplacement(sb, Matcher.quoteReplacement(part1 + part2));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Chuẩn hóa khoảng trắng từng dòng. Nếu là code block (thụt lề hoặc có cú pháp lập trình), giữ nguyên thụt lề.
     */
    private String normalizeLinesAndWhitespace(String text, boolean preserveCode) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                result.append("\n");
                continue;
            }

            if (preserveCode && isCodeOrTableLine(line)) {
                // Giữ nguyên thụt dòng ban đầu, chỉ bỏ trailing whitespace
                result.append(line.replaceAll("\\s+$", "")).append("\n");
            } else {
                // Văn bản thông thường: rút gọn khoảng trắng giữa các từ
                String collapsed = trimmed.replaceAll("[\\t\\f ]+", " ");
                result.append(collapsed).append("\n");
            }
        }

        String finalResult = result.toString();
        return EXCESSIVE_NEWLINES.matcher(finalResult).replaceAll("\n\n");
    }

    /**
     * Nhận diện dòng có khả năng là code hoặc bảng biểu (chứa thụt lề chuẩn, cú pháp lập trình, hoặc ký tự phân cách bảng |)
     */
    private boolean isCodeOrTableLine(String line) {
        if (line.startsWith("    ") || line.startsWith("\t")) {
            return true;
        }
        String stripped = line.strip();
        if (stripped.startsWith("|") && stripped.endsWith("|")) {
            return true; // Dòng bảng Markdown
        }
        return stripped.startsWith("public ") ||
                stripped.startsWith("private ") ||
                stripped.startsWith("protected ") ||
                stripped.startsWith("import ") ||
                stripped.startsWith("package ") ||
                stripped.startsWith("class ") ||
                stripped.startsWith("def ") ||
                stripped.startsWith("for (") ||
                stripped.startsWith("while (") ||
                stripped.startsWith("if (") ||
                stripped.endsWith("{") ||
                stripped.equals("}") ||
                stripped.equals("};");
    }

    /**
     * Tự động quét và phát hiện các dòng header/footer lặp lại trên >= 3 trang (hoặc >= 50% số trang).
     */
    private Set<String> detectRepeatedHeaderFooters(List<ExtractedPage> pages) {
        Set<String> repeated = new HashSet<>();
        if (pages.size() < 3) {
            return repeated;
        }

        Map<String, Integer> lineFrequency = new HashMap<>();
        int validPageCount = 0;

        for (ExtractedPage page : pages) {
            String[] lines = page.getText().split("\\r?\\n");
            List<String> meaningfulLines = new ArrayList<>();
            for (String l : lines) {
                String t = l.trim();
                if (!t.isEmpty()) {
                    meaningfulLines.add(t);
                }
            }

            if (meaningfulLines.isEmpty()) {
                continue;
            }
            validPageCount++;

            // Lấy 2 dòng đầu tiên (khả năng là header)
            int headerLines = Math.min(2, meaningfulLines.size());
            for (int i = 0; i < headerLines; i++) {
                String h = meaningfulLines.get(i);
                lineFrequency.put(h, lineFrequency.getOrDefault(h, 0) + 1);
            }

            // Lấy 2 dòng cuối cùng (khả năng là footer hoặc số trang)
            int footerStart = Math.max(headerLines, meaningfulLines.size() - 2);
            for (int i = footerStart; i < meaningfulLines.size(); i++) {
                String f = meaningfulLines.get(i);
                lineFrequency.put(f, lineFrequency.getOrDefault(f, 0) + 1);
            }
        }

        int threshold = Math.max(3, (int) Math.ceil(validPageCount * 0.5));
        for (Map.Entry<String, Integer> entry : lineFrequency.entrySet()) {
            if (entry.getValue() >= threshold) {
                repeated.add(entry.getKey());
            }
        }

        return repeated;
    }

    /**
     * Loại bỏ các dòng là header/footer hoặc số trang đứng riêng lẻ khỏi trang văn bản.
     */
    private String stripHeaderFooters(String pageText, Set<String> repeatedHeaderFooters) {
        String[] lines = pageText.split("\\r?\\n");
        List<String> keptLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                keptLines.add("");
                continue;
            }

            // Kiểm tra xem dòng có nằm trong danh sách lặp lại không
            if (repeatedHeaderFooters.contains(trimmed)) {
                continue;
            }

            // Kiểm tra nếu là dòng đầu hoặc dòng cuối và có cấu trúc số trang đơn thuần
            boolean isEdgeLine = (i <= 1 || i >= lines.length - 2);
            if (isEdgeLine && STANDALONE_PAGE_NUM.matcher(trimmed).matches()) {
                continue;
            }

            keptLines.add(lines[i]);
        }

        return String.join("\n", keptLines);
    }
}
