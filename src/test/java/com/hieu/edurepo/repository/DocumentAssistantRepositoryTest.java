package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DocumentAssistantRepositoryTest {

    @Autowired
    private DocumentAssistantRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsPublishedDocumentByTitleWithoutCaseSensitivity() {
        persistDocument("Giáo trình Java căn bản", "Lập trình nhập môn", "oop", DocumentStatus.PUBLISHED,
                null, null);

        Page<Document> result = search("JAVA", 5);

        assertThat(result.getContent()).extracting(Document::getTitle)
                .containsExactly("Giáo trình Java căn bản");
    }

    @Test
    void findsByDescriptionKeywordsCategoryAndAuthor() {
        Category category = new Category();
        category.setName("Công nghệ phần mềm");
        category = entityManager.persist(category);
        User creator = new User();
        creator.setUsername("lecturer@edurepo.test");
        creator.setEmail("lecturer@edurepo.test");
        creator.setPassword("test-only");
        creator.setFullName("Nguyễn Minh An");
        creator = entityManager.persist(creator);

        persistDocument("Kiến trúc ứng dụng", "Thiết kế hệ thống nhiều tầng", "spring, patterns",
                DocumentStatus.PUBLISHED, category, creator);

        assertThat(search("nhiều tầng", 5).getContent()).hasSize(1);
        assertThat(search("spring", 5).getContent()).hasSize(1);
        assertThat(search("Công nghệ phần mềm", 5).getContent()).hasSize(1);
        assertThat(search("Nguyễn Minh An", 5).getContent()).hasSize(1);
    }

    @Test
    void neverReturnsUnpublishedDocuments() {
        persistDocument("Tài liệu nội bộ Java", "Không được công khai", "java", DocumentStatus.APPROVED,
                null, null);

        assertThat(search("Java", 5).getContent()).isEmpty();
    }

    @Test
    void fuzzyCandidatePoolContainsOnlyPublishedDocuments() {
        persistDocument("Java công khai", "Mô tả", "java", DocumentStatus.PUBLISHED, null, null);
        persistDocument("Java nội bộ", "Mô tả", "java", DocumentStatus.APPROVED, null, null);
        entityManager.flush();
        entityManager.clear();

        List<Document> candidates = repository.findPublishedCandidates(PageRequest.of(0, 20));

        assertThat(candidates).extracting(Document::getTitle).containsExactly("Java công khai");
    }

    @Test
    void returnsNoResultForUnknownKeyword() {
        persistDocument("Cơ sở dữ liệu", "SQL cơ bản", "mysql", DocumentStatus.PUBLISHED, null, null);

        assertThat(search("không tồn tại", 5).getContent()).isEmpty();
    }

    @Test
    void limitsOneAssistantResponseToFiveDocuments() {
        for (int index = 1; index <= 7; index++) {
            persistDocument("Java số " + index, "Mô tả", "java", DocumentStatus.PUBLISHED, null, null);
        }

        Page<Document> result = search("Java", 5);

        assertThat(result.getContent()).hasSize(5);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.getTotalElements()).isEqualTo(7);
    }

    @Test
    void structuredSearchCanFilterByTopicAuthorAndYear() {
        Category database = new Category();
        database.setName("Cơ sở dữ liệu");
        database = entityManager.persist(database);

        Document document = persistDocument("Giáo trình SQL", "Thiết kế bảng", "database",
                DocumentStatus.PUBLISHED, database, null);
        document.setAuthorName("Nguyễn Văn A");
        document.setPublishedAt(LocalDateTime.of(2025, 3, 10, 9, 0));

        Page<Document> result = structured("", "cơ sở dữ liệu", "Nguyễn Văn A", "", 2025,
                Sort.by(Sort.Order.desc("publishedAt")));

        assertThat(result.getContent()).extracting(Document::getTitle).containsExactly("Giáo trình SQL");
    }

    @Test
    void relevanceSearchPrefersTitleBeforeDescriptionMatches() {
        Document descriptionMatch = persistDocument("Kiến trúc phần mềm", "Có ví dụ Java", "patterns",
                DocumentStatus.PUBLISHED, null, null);
        descriptionMatch.setPublishedAt(LocalDateTime.of(2026, 1, 2, 9, 0));
        Document titleMatch = persistDocument("Java căn bản", "Nhập môn lập trình", "oop",
                DocumentStatus.PUBLISHED, null, null);
        titleMatch.setPublishedAt(LocalDateTime.of(2025, 1, 2, 9, 0));

        entityManager.flush();
        entityManager.clear();
        Page<Document> result = repository.searchPublishedRelevant("Java", "", "", "", null, PageRequest.of(0, 5));

        assertThat(result.getContent()).extracting(Document::getTitle)
                .containsExactly("Java căn bản", "Kiến trúc phần mềm");
    }

    @Test
    void structuredSearchSupportsPopularSorting() {
        Document quiet = persistDocument("Tài liệu ít xem", "Mô tả", "java", DocumentStatus.PUBLISHED, null, null);
        quiet.setViewCount(2);
        quiet.setDownloadCount(1);
        Document popular = persistDocument("Tài liệu nhiều xem", "Mô tả", "java", DocumentStatus.PUBLISHED, null, null);
        popular.setViewCount(12);
        popular.setDownloadCount(5);

        Page<Document> result = structured("", "", "", "", null,
                Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("downloadCount")));

        assertThat(result.getContent()).extracting(Document::getTitle)
                .containsExactly("Tài liệu nhiều xem", "Tài liệu ít xem");
    }

    private Page<Document> search(String keyword, int size) {
        entityManager.flush();
        entityManager.clear();
        return repository.searchPublished(keyword,
                PageRequest.of(0, size, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
    }

    @Test
    void structuredSearchCanFilterByLanguageCode() {
        Document english = persistDocument("English Java Guide", "Guide", "java", DocumentStatus.PUBLISHED,
                null, null);
        english.setLanguageCode("en");
        Document vietnamese = persistDocument("Giáo trình Java", "Tài liệu", "java", DocumentStatus.PUBLISHED,
                null, null);
        vietnamese.setLanguageCode("vi");

        Page<Document> result = structured("", "", "", "en", null,
                Sort.by(Sort.Order.desc("publishedAt")));

        assertThat(result.getContent()).extracting(Document::getTitle).containsExactly("English Java Guide");
    }

    private Page<Document> structured(String keyword, String topic, String author,
                                      String languageCode, Integer year, Sort sort) {
        entityManager.flush();
        entityManager.clear();
        return repository.searchPublishedStructured(keyword, topic, author, languageCode, year,
                PageRequest.of(0, 5, sort));
    }

    private Document persistDocument(String title, String description, String keywords, DocumentStatus status,
                                     Category category, User creator) {
        Document document = new Document();
        document.setTitle(title);
        document.setDescription(description);
        document.setKeywords(keywords);
        document.setAuthorName("Tác giả EduRepo");
        document.setStatus(status);
        document.setCategory(category);
        document.setCreatedBy(creator);
        document.setPublishedAt(LocalDateTime.now());
        return entityManager.persist(document);
    }
}
