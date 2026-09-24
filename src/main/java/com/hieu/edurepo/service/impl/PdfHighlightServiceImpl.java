package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.service.PdfHighlightService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class PdfHighlightServiceImpl implements PdfHighlightService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfHighlightServiceImpl.class);

    // Bộ nhớ đệm LRU lưu kết quả bôi vàng PDF
    private final Map<String, HighlightResult> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, HighlightResult> eldest) {
                    return size() > 50;
                }
            }
    );

    // Bộ nhớ đệm LRU lưu vị trí trang chứa đoạn trích dẫn
    private final Map<String, Integer> pageCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > 100;
                }
            }
    );

    record CharPos(char ch, float x, float y, float width, float height) {}

    static class PageCharLocator extends PDFTextStripper {
        final List<CharPos> charPositions = new ArrayList<>(2048);
        final StringBuilder text = new StringBuilder(2048);

        PageCharLocator() throws IOException {
            super();
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) {
            for (TextPosition tp : textPositions) {
                String unicode = tp.getUnicode();
                if (unicode != null && !unicode.isEmpty()) {
                    char c = unicode.charAt(0);
                    charPositions.add(new CharPos(c, tp.getXDirAdj(), tp.getYDirAdj(), tp.getWidthDirAdj(), tp.getHeightDir()));
                    text.append(c);
                }
            }
            text.append(" ");
            if (!charPositions.isEmpty()) {
                CharPos last = charPositions.getLast();
                charPositions.add(new CharPos(' ', last.x() + last.width(), last.y(), 4f, last.height()));
            }
        }
    }

    @Override
    public Integer locatePage(Resource pdfResource, String phrase, Integer hintPage) {
        if (pdfResource == null || phrase == null || phrase.isBlank()) {
            return hintPage;
        }

        String pageKey = (pdfResource.getFilename() != null ? pdfResource.getFilename() : "doc")
                + "_" + phrase.trim().toLowerCase().hashCode();
        Integer cachedPage = pageCache.get(pageKey);
        if (cachedPage != null) {
            return cachedPage;
        }

        List<String> candidatePhrases = buildCandidatePhrases(phrase);
        if (candidatePhrases.isEmpty()) {
            return hintPage;
        }

        try (InputStream in = pdfResource.getInputStream();
             PDDocument document = Loader.loadPDF(in.readAllBytes())) {

            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) return hintPage;

            List<Integer> pagesToScan = buildCandidatePagesToScan(hintPage, totalPages);
            PDFTextStripper stripper = new PDFTextStripper();

            int tocFallbackPage = -1;

            for (int pageNum : pagesToScan) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document).toLowerCase();
                boolean isToc = isTableOfContentsText(pageText);

                for (String candidate : candidatePhrases) {
                    if (pageText.contains(candidate.toLowerCase())) {
                        if (!isToc) {
                            pageCache.put(pageKey, pageNum);
                            return pageNum;
                        } else if (tocFallbackPage == -1) {
                            tocFallbackPage = pageNum;
                        }
                    }
                }
            }

            if (tocFallbackPage != -1 && hintPage == null) {
                pageCache.put(pageKey, tocFallbackPage);
                return tocFallbackPage;
            }

        } catch (Exception e) {
            LOGGER.warn("Không thể tìm trang chứa đoạn trích dẫn: {}", e.getMessage());
        }

        return hintPage;
    }

    @Override
    public HighlightResult highlightWithResult(Resource pdfResource, String phrase, Integer targetPage) {
        if (pdfResource == null || phrase == null || phrase.isBlank()) {
            return null;
        }

        String cacheKey = (pdfResource.getFilename() != null ? pdfResource.getFilename() : "doc")
                + "_" + (targetPage != null ? targetPage : "auto") + "_" + phrase.trim().toLowerCase().hashCode();
        HighlightResult cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<String> candidatePhrases = buildCandidatePhrases(phrase);
        if (candidatePhrases.isEmpty()) {
            return null;
        }

        try (InputStream in = pdfResource.getInputStream();
             PDDocument document = Loader.loadPDF(in.readAllBytes())) {

            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) return null;

            List<Integer> pagesToScan = buildCandidatePagesToScan(targetPage, totalPages);
            PDFTextStripper stripper = new PDFTextStripper();

            int matchedPage = -1;
            List<Rectangle2D.Float> matchedBoxes = null;
            int tocFallbackPage = -1;
            List<Rectangle2D.Float> tocFallbackBoxes = null;

            // 1. Quét nhanh bằng PDFTextStripper để tìm trang chính xác
            for (int pageNum : pagesToScan) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document).toLowerCase();
                boolean isToc = isTableOfContentsText(pageText);

                boolean pageHasMatch = false;
                for (String candidate : candidatePhrases) {
                    if (pageText.contains(candidate.toLowerCase())) {
                        pageHasMatch = true;
                        break;
                    }
                }

                if (pageHasMatch) {
                    // 2. Chạy PageCharLocator chi tiết CHỈ trên trang đã tìm thấy để lấy tọa độ bôi vàng
                    PDPage page = document.getPage(pageNum - 1);
                    PageCharLocator locator = new PageCharLocator();
                    locator.setStartPage(pageNum);
                    locator.setEndPage(pageNum);
                    locator.getText(document);

                    List<Rectangle2D.Float> boxes = findBoxesOnPage(locator, candidatePhrases);
                    if (!boxes.isEmpty()) {
                        if (!isToc || (targetPage != null && targetPage == pageNum)) {
                            matchedPage = pageNum;
                            matchedBoxes = boxes;
                            applyHighlightToPage(document, page, boxes);
                            break;
                        } else if (tocFallbackPage == -1) {
                            tocFallbackPage = pageNum;
                            tocFallbackBoxes = boxes;
                        }
                    }
                }
            }

            if (matchedPage == -1 && tocFallbackPage != -1 && targetPage == null) {
                matchedPage = tocFallbackPage;
                matchedBoxes = tocFallbackBoxes;
                PDPage page = document.getPage(tocFallbackPage - 1);
                applyHighlightToPage(document, page, tocFallbackBoxes);
            }

            if (matchedPage == -1 || matchedBoxes == null || matchedBoxes.isEmpty()) {
                return null;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            byte[] resultBytes = baos.toByteArray();

            HighlightResult res = new HighlightResult(resultBytes, matchedPage, true);
            cache.put(cacheKey, res);

            String pageKey = (pdfResource.getFilename() != null ? pdfResource.getFilename() : "doc")
                    + "_" + phrase.trim().toLowerCase().hashCode();
            pageCache.put(pageKey, matchedPage);

            return res;

        } catch (Exception e) {
            LOGGER.warn("Không thể bôi vàng đoạn trích dẫn trong PDF: {}", e.getMessage());
            return null;
        }
    }

    private List<Integer> buildCandidatePagesToScan(Integer targetPage, int totalPages) {
        Set<Integer> visited = new LinkedHashSet<>();
        // 1. Kiểm tra trang mục tiêu trước tiên
        if (targetPage != null && targetPage >= 1 && targetPage <= totalPages) {
            visited.add(targetPage);
            // 2. Quét các trang lân cận (+/- 5 trang)
            for (int offset = 1; offset <= 5; offset++) {
                if (targetPage - offset >= 1) visited.add(targetPage - offset);
                if (targetPage + offset <= totalPages) visited.add(targetPage + offset);
            }
        }

        // 3. Quét toàn bộ các trang còn lại trong tài liệu
        for (int p = 1; p <= totalPages; p++) {
            visited.add(p);
        }

        return new ArrayList<>(visited);
    }

    private boolean isTableOfContentsText(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        if (lower.contains("table of contents") || lower.contains("mục lục") || lower.contains("danh mục")) {
            return true;
        }
        // Dấu chấm phân cách số trang trong mục lục (leader dots vd: ". . . . . 7" hoặc "...... 7")
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:\\.\\s*){4,}").matcher(text);
        int dotMatches = 0;
        while (m.find()) {
            dotMatches++;
            if (dotMatches >= 2) return true;
        }
        return false;
    }

    record NormalizedMapping(String normalizedText, int[] origCharIndices) {}

    private NormalizedMapping buildNormalizedMapping(List<CharPos> positions) {
        StringBuilder norm = new StringBuilder(positions.size());
        int[] map = new int[positions.size() * 2];
        int normLen = 0;

        boolean prevWasSpace = false;
        for (int i = 0; i < positions.size(); i++) {
            char c = positions.get(i).ch();

            // Xử lý từ bị ngắt dòng có dấu gạch nối (hyphenation: ví dụ "sup-" cuối dòng nối tiếp "ports")
            if ((c == '-' || c == '\u00AD' || c == '—' || c == '–') && i + 1 < positions.size()) {
                int nextNonSpace = i + 1;
                while (nextNonSpace < positions.size() && Character.isWhitespace(positions.get(nextNonSpace).ch())) {
                    nextNonSpace++;
                }
                if (nextNonSpace < positions.size() && Character.isLetterOrDigit(positions.get(nextNonSpace).ch())
                        && i > 0 && Character.isLetterOrDigit(positions.get(i - 1).ch())) {
                    i = nextNonSpace - 1;
                    continue;
                }
            }

            if (Character.isWhitespace(c)) {
                if (!prevWasSpace && normLen > 0) {
                    norm.append(' ');
                    map[normLen++] = i;
                    prevWasSpace = true;
                }
            } else {
                norm.append(Character.toLowerCase(c));
                map[normLen++] = i;
                prevWasSpace = false;
            }
        }

        int[] finalMap = Arrays.copyOf(map, normLen);
        return new NormalizedMapping(norm.toString(), finalMap);
    }

    private List<Rectangle2D.Float> findBoxesOnPage(PageCharLocator locator, List<String> candidatePhrases) {
        if (locator.charPositions.isEmpty() || candidatePhrases.isEmpty()) {
            return List.of();
        }

        NormalizedMapping mapping = buildNormalizedMapping(locator.charPositions);
        for (String candidate : candidatePhrases) {
            List<Rectangle2D.Float> boxes = matchPhraseBoundingBoxesWithMapping(locator.charPositions, mapping, candidate);
            if (!boxes.isEmpty()) {
                return boxes;
            }
        }
        return List.of();
    }

    private List<Rectangle2D.Float> matchPhraseBoundingBoxesWithMapping(List<CharPos> positions, NormalizedMapping mapping, String phrase) {
        List<Rectangle2D.Float> boxes = new ArrayList<>();
        if (positions.isEmpty() || mapping.origCharIndices().length == 0 || phrase == null || phrase.isBlank()) {
            return boxes;
        }

        // Chuẩn hóa cụm từ tìm kiếm tương đương (xóa ký tự markdown, dấu ngoặc kép, khoảng trắng thừa)
        String normPhrase = phrase.toLowerCase()
                .replaceAll("[*_#`~=\"“”'’\\[\\]]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normPhrase.length() < 3) return boxes;

        int matchIdx = mapping.normalizedText().indexOf(normPhrase);
        int matchLen = normPhrase.length();

        // Fallback 1: Trượt cửa sổ tìm các cụm 6 từ, 5 từ, 4 từ, 3 từ
        if (matchIdx == -1) {
            String[] words = normPhrase.split("\\s+");
            for (int w = Math.min(words.length - 1, 6); w >= 3; w--) {
                String sub = String.join(" ", Arrays.copyOfRange(words, 0, w));
                matchIdx = mapping.normalizedText().indexOf(sub);
                if (matchIdx != -1) {
                    matchLen = sub.length();
                    break;
                }
            }
        }

        // Fallback 2: Thử bất kỳ chuỗi 3 từ liên tiếp có độ dài >= 10 ký tự
        if (matchIdx == -1) {
            String[] words = normPhrase.split("\\s+");
            for (int i = 0; i <= words.length - 3; i++) {
                String sub = words[i] + " " + words[i + 1] + " " + words[i + 2];
                if (sub.length() >= 10) {
                    matchIdx = mapping.normalizedText().indexOf(sub);
                    if (matchIdx != -1) {
                        matchLen = sub.length();
                        break;
                    }
                }
            }
        }

        if (matchIdx == -1 || matchIdx >= mapping.origCharIndices().length) {
            return boxes;
        }

        int origStart = mapping.origCharIndices()[matchIdx];
        int endMappingIdx = Math.min(matchIdx + matchLen - 1, mapping.origCharIndices().length - 1);
        int origEnd = Math.min(mapping.origCharIndices()[endMappingIdx] + 1, positions.size());

        Float lineMinX = null;
        Float lineMaxX = null;
        Float lineY = null;
        Float lineHeight = null;

        for (int i = origStart; i < origEnd; i++) {
            CharPos cp = positions.get(i);
            if (Character.isWhitespace(cp.ch())) continue;

            if (lineY == null) {
                lineMinX = cp.x();
                lineMaxX = cp.x() + cp.width();
                lineY = cp.y();
                lineHeight = cp.height();
            } else if (Math.abs(cp.y() - lineY) > lineHeight * 0.6f) {
                // Sang dòng mới trong đoạn văn
                boxes.add(new Rectangle2D.Float(lineMinX, lineY, lineMaxX - lineMinX, lineHeight));
                lineMinX = cp.x();
                lineMaxX = cp.x() + cp.width();
                lineY = cp.y();
                lineHeight = cp.height();
            } else {
                // Cùng một dòng
                lineMinX = Math.min(lineMinX, cp.x());
                lineMaxX = Math.max(lineMaxX, cp.x() + cp.width());
                lineHeight = Math.max(lineHeight, cp.height());
            }
        }

        if (lineMinX != null && lineMaxX != null) {
            boxes.add(new Rectangle2D.Float(lineMinX, lineY, lineMaxX - lineMinX, lineHeight));
        }

        return boxes;
    }

    private void applyHighlightToPage(PDDocument document, PDPage page, List<Rectangle2D.Float> boxes) throws IOException {
        PDRectangle mediaBox = page.getMediaBox();
        float pageHeight = mediaBox.getHeight();

        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        // Độ trong suốt 38%: Nổi bật sắc vàng rực rỡ nhưng chữ bên dưới đọc rõ 100%
        gs.setNonStrokingAlphaConstant(0.38f);

        try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.setGraphicsStateParameters(gs);
            // Vàng nhạt dạ quang chuẩn highlighter: #fef08a (RGB: 0.996f, 0.941f, 0.541f)
            cs.setNonStrokingColor(0.996f, 0.941f, 0.541f);
            for (Rectangle2D.Float box : boxes) {
                float pdfY = pageHeight - box.y - box.height;
                cs.addRect(box.x - 2, pdfY - 1.5f, box.width + 4, box.height + 3f);
            }
            cs.fill();
        }
    }

    public List<String> buildCandidatePhrases(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> candidates = new ArrayList<>();

        String clean = raw
                .replaceAll("[*_#`~=]+", " ")
                .replaceAll("(?m)^[>\\s*-]+", "")
                .replaceAll("(?i)\\b(Description|Action|Note|Lưu ý|Mô tả|Ví dụ)\\s*:\\s*", "")
                .trim();

        // 1. Tìm các câu hoàn chỉnh
        String[] sentences = clean.split("[.?!;\\n\\r]+");
        for (String s : sentences) {
            String trimmed = s.trim();
            String[] words = trimmed.split("\\s+");
            if (words.length >= 4) {
                // Thử câu đầy đủ (tối đa 8 từ)
                candidates.add(String.join(" ", Arrays.copyOfRange(words, 0, Math.min(words.length, 8))));
                // Thử 4 từ đầu
                candidates.add(String.join(" ", Arrays.copyOfRange(words, 0, 4)));
            } else if (words.length >= 2) {
                candidates.add(trimmed);
            }
        }

        // 2. Toàn bộ chuỗi sạch (cắt ngắn 50 ký tự)
        if (clean.length() > 5) {
            candidates.add(clean.substring(0, Math.min(clean.length(), 50)));
        }

        return candidates.stream().filter(s -> s.length() >= 4).distinct().toList();
    }
}
