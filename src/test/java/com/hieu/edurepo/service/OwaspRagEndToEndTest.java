package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.dto.RagAnswer;
import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.CategoryRepository;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;

@SpringBootTest
@Transactional
class OwaspRagEndToEndTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OwaspRagEndToEndTest.class);

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private DocumentIndexingService indexingService;

    @Autowired
    private DocumentAssistantService assistantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("End-to-End RAG: OWASP testing guide generates AI RAG_ANSWER with sources")
    void testOwaspRagGeneratesAiAnswer() {
        // 1. Setup user & category
        var role = roleRepository.findByName(RoleName.USER).orElseGet(() -> {
            var r = new com.hieu.edurepo.entity.Role();
            r.setName(RoleName.USER);
            return roleRepository.save(r);
        });

        User author = new User();
        author.setUsername("security_tester");
        author.setEmail("security.tester@edurepo.local");
        author.setPassword("hashed-password");
        author.setFullName("Chuyên gia An toàn thông tin");
        author.getRoles().add(role);
        author = userRepository.save(author);

        Category category = new Category();
        category.setName("An Toàn Thông Tin");
        category = categoryRepository.save(category);

        // 2. Tạo tài liệu OWASP Web Security Testing Guide v4.2
        Document doc = new Document();
        doc.setTitle("OWASP Web Security Testing Guide v4.2");
        doc.setDescription("Hướng dẫn kiểm thử bảo mật ứng dụng web toàn diện theo chuẩn OWASP WSTG v4.2. "
                + "Quy trình kiểm thử bảo mật web theo chuẩn OWASP bao gồm các bước và giai đoạn chính: "
                + "1. Thu thập thông tin (Information Gathering): Nhận diện công nghệ, thăm dò mạng và dịch vụ. "
                + "2. Kiểm thử quản lý cấu hình và triển khai (Configuration and Deployment Management Testing). "
                + "3. Kiểm thử quản lý danh tính (Identity Management Testing): Đăng ký tài khoản, phân quyền. "
                + "4. Kiểm thử xác thực (Authentication Testing): Brute-force, kiểm tra độ mạnh mật khẩu, nhớ mật khẩu. "
                + "5. Kiểm thử phân quyền (Authorization Testing): Directory traversal, Bypass authorization, BOLA/IDOR. "
                + "6. Kiểm thử quản lý phiên (Session Management Testing): Session hijacking, CSRF, Token expiration. "
                + "7. Kiểm thử kiểm tra đầu vào (Input Validation Testing): SQL Injection, XSS, Command Injection, SSRF. "
                + "8. Kiểm thử xử lý lỗi (Error Handling): Thông tin rò rỉ qua stack trace. "
                + "9. Kiểm thử mật mã (Cryptography Testing): SSL/TLS ciphers, lưu trữ dữ liệu nhạy cảm. "
                + "10. Kiểm thử logic nghiệp vụ (Business Logic Testing). "
                + "11. Kiểm thử phía máy khách (Client-side Testing): DOM-based XSS, CORS, Web Storage.");
        doc.setKeywords("OWASP, WSTG, Web Security Testing, Kiem thu bao mat web, An toan ung dung");
        doc.setStatus(DocumentStatus.PUBLISHED);
        doc.setCreatedBy(author);
        doc.setCategory(category);
        doc = documentRepository.save(doc);

        // 3. Index tài liệu (gọi Embedding API sinh vector và lưu vào chunks)
        indexingService.indexDocument(doc);
        long chunkCount = chunkRepository.countByDocumentId(doc.getId());
        assertTrue(chunkCount > 0, "OWASP document must have at least 1 indexed chunk");

        // 4. Test RAG Assistant với câu hỏi của người dùng
        String userQuery = "Giải thích các bước kiểm thử bảo mật web theo chuẩn OWASP";
        DocumentAssistantResponse response = assistantService.respond(userQuery);

        LOGGER.info("=== RAG ASSISTANT RESPONSE ===");
        LOGGER.info("Type: {}", response.type());
        LOGGER.info("Message: {}", response.message());
        LOGGER.info("Documents count: {}", response.documents().size());
        if (!response.documents().isEmpty()) {
            LOGGER.info("First source doc: {}", response.documents().get(0).title());
        }

        assertNotNull(response, "Response should not be null");
        assertEquals("RAG_ANSWER", response.type(), "Response type MUST be RAG_ANSWER, not plain RESULTS");
        assertNotNull(response.message(), "AI answer message should not be null");
        assertFalse(response.message().isBlank(), "AI answer message should not be empty");
        assertNotEquals(RagAnswer.NOT_FOUND_MESSAGE, response.message(), "Should not return NOT_FOUND_MESSAGE");

        // Đảm bảo trả lời đúng nội dung OWASP và chứa nguồn tài liệu
        assertFalse(response.documents().isEmpty(), "Source documents should be included in the response");
        assertEquals(doc.getId(), response.documents().get(0).id(), "Source document should match the indexed OWASP doc");

        // 5. Test Anti-Hallucination với câu hỏi ngoài phạm vi học liệu
        DocumentAssistantResponse unknownResponse = assistantService.respond("Công thức nấu phở bò truyền thống ngon nhất là gì?");
        assertNotNull(unknownResponse);
        assertTrue(Set.of("NO_RESULTS", "RAG_INSUFFICIENT").contains(unknownResponse.type()),
                "Off-topic query must not be answered from outside EduRepo");
        assertNotNull(unknownResponse.message(), "Off-topic query must return a message from AI");
    }
}
