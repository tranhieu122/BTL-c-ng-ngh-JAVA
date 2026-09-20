package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.dto.RagAnswer;
import com.hieu.edurepo.dto.RagSearchResult;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RagPipelineIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private DocumentIndexingService indexingService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private DocumentAssistantService assistantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void testEndToEndIndexingAndRetrieval() {
        // 1. Tạo user và category
        var role = roleRepository.findByName(RoleName.USER).orElseGet(() -> {
            var r = new com.hieu.edurepo.entity.Role();
            r.setName(RoleName.USER);
            return roleRepository.save(r);
        });

        User author = new User();
        author.setUsername("author_rag");
        author.setEmail("author.rag@edurepo.local");
        author.setPassword("hashed-password");
        author.setFullName("Nguyen Van A");
        author.getRoles().add(role);
        author = userRepository.save(author);

        Category category = new Category();
        category.setName("Lap Trinh Java");
        category = categoryRepository.save(category);

        // 2. Tạo tài liệu PUBLISHED
        Document doc = new Document();
        doc.setTitle("Huong dan xay dung ung dung voi Spring Boot");
        doc.setDescription("Tai lieu huong dan lap trinh REST API su dung Spring Boot va Spring Data JPA toan dien.");
        doc.setKeywords("Spring Boot, Java, REST API, JPA");
        doc.setStatus(DocumentStatus.PUBLISHED);
        doc.setCreatedBy(author);
        doc.setCategory(category);
        doc = documentRepository.save(doc);

        // 3. Index tài liệu
        indexingService.indexDocument(doc);
        long chunkCount = chunkRepository.countByDocumentId(doc.getId());
        assertTrue(chunkCount > 0, "Document should have at least 1 indexed chunk");

        // 4. Semantic / Lexical Retrieval
        List<RagSearchResult> results = retrievalService.retrieve("Spring Boot");
        assertFalse(results.isEmpty(), "Retrieval should return matching chunks for 'Spring Boot'");
        assertEquals(doc.getId(), results.get(0).document().getId());

        // 5. Test Assistant Response
        DocumentAssistantResponse response = assistantService.respond("Huong dan Spring Boot");
        assertNotNull(response);
        assertFalse(response.documents().isEmpty());
        assertEquals(doc.getId(), response.documents().get(0).id());

        // 6. Test Anti-Hallucination: Câu hỏi hoàn toàn không có trong kho học liệu
        DocumentAssistantResponse unknownResponse = assistantService.respond("Công thức nấu phở bò truyền thống ngon nhất là gì?");
        assertNotNull(unknownResponse);
        assertTrue(Set.of("NO_RESULTS", "RAG_INSUFFICIENT").contains(unknownResponse.type()));
        assertTrue(unknownResponse.message().contains("EduRepo") || unknownResponse.message().contains("chưa đủ"));

        // 7. Test Unpublished Document Removal
        doc.setStatus(DocumentStatus.DRAFT);
        documentRepository.save(doc);
        indexingService.indexDocument(doc); // Nên tự động xóa index vì status != PUBLISHED

        long countAfterUnpublish = chunkRepository.countByDocumentId(doc.getId());
        assertEquals(0, countAfterUnpublish, "Chunks must be deleted when document is unpublished");
    }
}
