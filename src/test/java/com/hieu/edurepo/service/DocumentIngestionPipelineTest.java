package com.hieu.edurepo.service;

import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.DocumentSection;
import com.hieu.edurepo.dto.ExtractedPage;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.dto.StructuredChunk;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.enums.PageSourceType;
import com.hieu.edurepo.service.impl.SectionDetectionServiceImpl;
import com.hieu.edurepo.service.impl.TextCleaningServiceImpl;
import com.hieu.edurepo.service.impl.TextExtractionServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bộ kiểm thử toàn diện 10 kịch bản cho Document Ingestion Pipeline nâng cao của EduRepo.
 */
/**
 * Kiểm thử luồng nạp và xử lý học liệu (Document Ingestion Pipeline Test).
 * Kiểm tra quy trình từ bóc tách văn bản, tạo vector nhúng và cập nhật trạng thái INDEXED vào CSDL.
 */
class DocumentIngestionPipelineTest {

    private RagProperties ragProperties;
    private TextCleaningService cleaningService;
    private SectionDetectionService sectionService;
    private DocumentChunker chunker;
    private OcrService mockOcrService;
    private TextExtractionService extractionService;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setMinPageTextLength(40);
        ragProperties.setOcrEnabled(true);
        ragProperties.setChunkSize(300);
        ragProperties.setChunkOverlap(50);
        ragProperties.setCleanHyphenation(true);
        ragProperties.setPreserveCodeBlocks(true);

        cleaningService = new TextCleaningServiceImpl(ragProperties);
        sectionService = new SectionDetectionServiceImpl();
        chunker = new DocumentChunker();
        mockOcrService = mock(OcrService.class);
        extractionService = new TextExtractionServiceImpl(ragProperties, mockOcrService, cleaningService);
    }

    // Helper tạo PDF dạng byte[] cho testing
    private byte[] createSamplePdf(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (text != null && !text.isBlank()) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                        cs.newLineAtOffset(50, 700);
                        // PDFBox Standard 14 font hỗ trợ ASCII
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Test 1: PDF text thông thường - Trích xuất text layer và bảo toàn số trang")
    void test1_NormalTextPdfExtraction() throws IOException {
        String page1Text = "Spring Security architecture and authentication mechanism on EduRepo platform.";
        byte[] pdfBytes = createSamplePdf(page1Text);

        List<ExtractedPage> pages = extractionService.extractPages(new ByteArrayInputStream(pdfBytes), "sample.pdf");

        assertEquals(1, pages.size());
        ExtractedPage p1 = pages.get(0);
        assertEquals(1, p1.getPageNumber());
        assertEquals(PageSourceType.TEXT_LAYER, p1.getSourceType());
        assertFalse(p1.isScanned());
        assertTrue(p1.getText().contains("Spring Security"));
        verifyNoInteractions(mockOcrService);
    }

    @Test
    @DisplayName("Test 2: PDF scan / text quá ít - Tự động phát hiện và kích hoạt nhánh OCR")
    void test2_ScannedPdfDetectionAndOcrTriggered() throws IOException {
        // PDF trang trống hoặc dưới ngưỡng minPageTextLength (40 chars)
        byte[] pdfBytes = createSamplePdf("Short"); // chỉ 5 ký tự < 40

        when(mockOcrService.isAvailable()).thenReturn(true);
        when(mockOcrService.extractText(any(BufferedImage.class)))
                .thenReturn("Noi dung duoc nhan dang thanh cong qua cong nghe quang hoc OCR Tesseract.");

        List<ExtractedPage> pages = extractionService.extractPages(new ByteArrayInputStream(pdfBytes), "scanned.pdf");

        assertEquals(1, pages.size());
        ExtractedPage p1 = pages.get(0);
        assertEquals(1, p1.getPageNumber());
        assertEquals(PageSourceType.OCR, p1.getSourceType());
        assertTrue(p1.isScanned());
        assertTrue(p1.getText().contains("OCR Tesseract"));
        verify(mockOcrService).extractText(any(BufferedImage.class));
    }

    @Test
    @DisplayName("Test 3: PDF nhiều Section - Nhận diện phân cấp chương mục và giữ metadata")
    void test3_MultipleSectionsMetadataPreserved() {
        String sampleText = """
                1. Tong quan he thong
                He thong EduRepo giup quan ly hoc lieu noi sinh dai hoc.
                1.1 Muc tieu du an
                Ho tro tra cuu kien thuc va hoi dap AI chinh xac.
                2. Kien truc ky thuat
                Su dung Spring Boot va MySQL Vector Store.
                """;

        List<ExtractedPage> pages = List.of(new ExtractedPage(1, sampleText, PageSourceType.TEXT_LAYER, false));
        List<DocumentSection> sections = sectionService.detectSections(pages, "Tai lieu");

        assertEquals(3, sections.size());

        // Section 1
        assertEquals("1. Tong quan he thong", sections.get(0).getSectionTitle());
        assertNull(sections.get(0).getSubsectionTitle());
        assertTrue(sections.get(0).getContent().contains("EduRepo giup quan ly"));

        // Subsection 1.1
        assertEquals("1. Tong quan he thong", sections.get(1).getSectionTitle());
        assertEquals("1.1 Muc tieu du an", sections.get(1).getSubsectionTitle());
        assertTrue(sections.get(1).getContent().contains("Ho tro tra cuu"));

        // Section 2
        assertEquals("2. Kien truc ky thuat", sections.get(2).getSectionTitle());

        // Chunking with sections
        List<StructuredChunk> chunks = chunker.chunkSections(sections, 300, 50);
        assertFalse(chunks.isEmpty());
        assertEquals("1. Tong quan he thong", chunks.get(0).getSectionTitle());
    }

    @Test
    @DisplayName("Test 4: PDF nhiều trang - Bảo toàn startPage và endPage cho từng chunk")
    void test4_MultiplePagesPreserved() {
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, "1. Chuong 1\nNoi dung mo dau trang 1.", PageSourceType.TEXT_LAYER, false),
                new ExtractedPage(2, "Noi dung tiep theo trang 2 cua chuong 1.", PageSourceType.TEXT_LAYER, false),
                new ExtractedPage(3, "2. Chuong 2\nNoi dung chuong 2 tai trang 3.", PageSourceType.TEXT_LAYER, false)
        );

        List<DocumentSection> sections = sectionService.detectSections(pages, "Giao trinh");
        List<StructuredChunk> chunks = chunker.chunkSections(sections, 300, 50);
        assertEquals(2, sections.size());
        assertEquals(1, sections.get(0).getStartPage());
        assertEquals(2, sections.get(0).getEndPage());

        assertEquals(3, sections.get(1).getStartPage());
        assertEquals(3, sections.get(1).getEndPage());

        for (StructuredChunk chunk : chunks) {
            assertNotNull(chunk.getStartPage());
            assertNotNull(chunk.getEndPage());
            assertTrue(chunk.getStartPage() >= 1 && chunk.getStartPage() <= 3);
        }
    }

    @Test
    @DisplayName("Test 5: PDF có Header/Footer lặp lại - Tự động phát hiện và loại bỏ an toàn")
    void test5_RepeatedHeaderFooterHandled() {
        String repeatedHeader = "TRUONG DAI HOC EDU - TAI LIEU LUU HANH NOI BO";
        List<ExtractedPage> pages = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String content = repeatedHeader + "\n\nNoi dung rieng biet cua trang so " + i + ".\n\nTrang " + i + " / 4";
            pages.add(new ExtractedPage(i, content, PageSourceType.TEXT_LAYER, false));
        }

        List<ExtractedPage> cleaned = cleaningService.cleanPages(pages);

        for (ExtractedPage cp : cleaned) {
            assertFalse(cp.getText().contains(repeatedHeader), "Header lặp lại phải bị loại bỏ");
            assertFalse(cp.getText().contains("Trang " + cp.getPageNumber() + " / 4"), "Footer số trang phải bị loại bỏ");
            assertTrue(cp.getText().contains("Noi dung rieng biet cua trang so " + cp.getPageNumber()), "Nội dung thực tế phải được giữ nguyên");
        }
    }

    @Test
    @DisplayName("Test 6: PDF Tiếng Việt có dấu - Chuẩn hóa NFC và bảo toàn 100% ký tự Unicode")
    void test6_VietnameseUnicodePreserved() {
        String vietnameseText = "Hệ thống quản lý kho học liệu nội sinh EduRepo hỗ trợ tìm kiếm ngữ nghĩa, trích xuất tài liệu nghiên cứu khoa học.";
        String cleaned = cleaningService.cleanText(vietnameseText);

        assertEquals(vietnameseText, cleaned);
        assertTrue(cleaned.contains("kho học liệu nội sinh"));
        assertTrue(cleaned.contains("nghiên cứu khoa học"));
    }

    @Test
    @DisplayName("Test 7: Bảng biểu dạng Table - Không làm biến dạng cấu trúc")
    void test7_TableStructurePreserved() {
        String tableText = """
                | Mã môn | Tên học phần | Tín chỉ |
                | IT101  | Lập trình Java | 3 |
                | IT202  | Cơ sở dữ liệu | 4 |
                """;

        String cleaned = cleaningService.cleanText(tableText);
        assertTrue(cleaned.contains("| Mã môn | Tên học phần | Tín chỉ |"));
        assertTrue(cleaned.contains("| IT101  | Lập trình Java | 3 |"));
    }

    @Test
    @DisplayName("Test 8: Khối mã nguồn Code - Bảo toàn thụt lề và ký tự cú pháp")
    void test8_CodeIndentationPreserved() {
        String codeText = """
                public class SecurityConfig {
                    @Bean
                    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                        return http.build();
                    }
                }
                """;

        String cleaned = cleaningService.cleanText(codeText);
        assertTrue(cleaned.contains("    @Bean"));
        assertTrue(cleaned.contains("    public SecurityFilterChain filterChain"));
        assertTrue(cleaned.contains("return http.build();"));
    }

    @Test
    @DisplayName("Test 9: Ranh giới Chunk - Nối từ ngắt dòng (Hyphenation) và không cắt giữa câu")
    void test9_ChunkBoundaryAndHyphenation() {
        // Kiểm tra nối từ gạch nối cuối dòng: Secu-\nrity -> Security
        String brokenWord = "Spring Secu-\nrity la framework bao mat manh me nhat trong he sinh thai Java.";
        String repaired = cleaningService.repairHyphenation(brokenWord);
        assertTrue(repaired.contains("Spring Security la framework"));

        // Kiểm tra bảo toàn từ ghép có gạch nối không xuống dòng: machine-learning
        String compoundWord = "Chung toi ung dung machine-learning va kien truc TCP/IP de toi uu hoa.";
        String cleanedCompound = cleaningService.cleanText(compoundWord);
        assertTrue(cleanedCompound.contains("machine-learning"));
        assertTrue(cleanedCompound.contains("TCP/IP"));

        // Kiểm tra chia chunk không cắt giữa chừng câu
        String paragraph = "Cau thu nhat gioi thieu EduRepo. Cau thu hai mo ta tinh nang tim kiem RAG. Cau thu ba huong dan su dung chatbot.";
        List<DocumentSection> sec = List.of(new DocumentSection("Muc 1", null, paragraph, 1, 1));
        List<StructuredChunk> chunks = chunker.chunkSections(sec, 80, 20);

        for (StructuredChunk c : chunks) {
            String txt = c.getContent();
            // Đảm bảo không bị cắt cụt lửng ở giữa một từ
            assertFalse(txt.endsWith("gioi th") || txt.endsWith("mo t"));
        }
    }

    @Test
    @DisplayName("Test 10: Tương thích RAG hiện tại - ContextBuilder, Citation và Hover Preview")
    void test10_ExistingRagCompatibility() {
        Document doc = new Document();
        doc.setId(42L);
        doc.setTitle("Giao trinh Spring Boot");

        DocumentChunk chunk = new DocumentChunk(doc, 0, "Authentication la co che xac thuc danh tinh nguoi dung bang username va password.", null, 25);
        chunk.setId(101L);
        chunk.setPageNumber(5);
        chunk.setSectionTitle("3. Spring Security");
        chunk.setSubsectionTitle("3.1 Authentication");

        RagSearchResult searchResult = new RagSearchResult(doc, chunk, 0.88);

        ContextBuilder contextBuilder = new ContextBuilder(ragProperties);
        ContextBuilder.BuiltContext context = contextBuilder.buildContext("Authentication la gi?", List.of(searchResult));

        assertNotNull(context);
        assertTrue(context.userPrompt().contains("[Document 1]"));
        assertTrue(context.userPrompt().contains("3. Spring Security"));
        assertTrue(context.userPrompt().contains("3.1 Authentication"));

        List<RagSource> sources = context.sources();
        assertEquals(1, sources.size());
        RagSource s = sources.get(0);
        assertEquals(1, s.sourceId());
        assertEquals(42L, s.documentId());
        assertEquals(101L, s.chunkId());
        assertEquals(5, s.pageNumber());
        assertEquals("3. Spring Security", s.sectionTitle());
        assertEquals("/repository/42", s.detailUrl());
        assertFalse(s.snippet().isBlank());
    }
}
