package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.repository.DocumentAssistantRepository;
import com.hieu.edurepo.service.impl.DocumentAssistantServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentAssistantServiceTest {

    @Test
    void rejectsEmptyAndOverlongMessagesBeforeQuerying() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);

        assertEquals("INVALID_INPUT", service.respond("   ").type());
        assertEquals("INVALID_INPUT", service.respond("x".repeat(201)).type());
        verifyNoInteractions(repository);
    }

    @Test
    void handlesGreetingAndOutOfScopeRequestWithoutQuerying() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);

        DocumentAssistantResponse greeting = service.respond("Xin chào!");
        DocumentAssistantResponse outOfScope = service.respond("Hãy viết code giúp tôi");

        assertEquals("GREETING", greeting.type());
        assertFalse(greeting.suggestions().isEmpty());
        assertEquals("OUT_OF_SCOPE", outOfScope.type());
        verifyNoInteractions(repository);
    }

    @Test
    void parsesAuthorRequest() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubRelevant(repository, List.of(publishedDocument(12L, "Giáo trình Java")));
        stubSuggestions(repository, List.of("Công nghệ phần mềm"));

        DocumentAssistantResponse response = service.respond("Tìm tài liệu của Nguyễn Văn A");

        ArgumentCaptor<String> author = ArgumentCaptor.forClass(String.class);
        verify(repository).searchPublishedRelevant(anyString(), anyString(), author.capture(), anyString(),
                isNull(), any(Pageable.class));
        assertEquals("Nguyễn Văn A", author.getValue());
        assertEquals("Nguyễn Văn A", response.filters().get("author"));
        assertEquals("RESULTS", response.type());
        assertEquals("/repository/12", response.documents().getFirst().detailUrl());
    }

    @Test
    void parsesTopicRequest() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubRelevant(repository, List.of(publishedDocument(12L, "Nhập môn cơ sở dữ liệu")));
        stubSuggestions(repository, List.of("Công nghệ phần mềm"));

        DocumentAssistantResponse response = service.respond("Cho mình xem tài liệu môn cơ sở dữ liệu");

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(repository).searchPublishedRelevant(anyString(), topic.capture(), anyString(), anyString(),
                isNull(), any(Pageable.class));
        assertEquals("cơ sở dữ liệu", topic.getValue());
        assertEquals("cơ sở dữ liệu", response.filters().get("topic"));
        assertTrue(response.allResultsUrl().contains("keyword=c%C6%A1%20s%E1%BB%9F%20d%E1%BB%AF%20li%E1%BB%87u"));
    }

    @Test
    void parsesNewestRequestAndSortsByPublishedDate() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubStructured(repository, List.of(publishedDocument(15L, "Spring Boot mới nhất")), 1);
        stubSuggestions(repository, List.of());

        DocumentAssistantResponse response = service.respond("Tài liệu Spring Boot mới nhất");

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchPublishedStructured(keyword.capture(), anyString(), anyString(), anyString(),
                isNull(), pageable.capture());
        assertEquals("Spring Boot", keyword.getValue());
        assertEquals("LATEST", response.filters().get("intent"));
        assertEquals("newest", response.filters().get("sortMode"));
        assertTrue(pageable.getValue().getSort().getOrderFor("publishedAt").isDescending());
    }

    @Test
    void treatsExistingDocumentsRequestAsNewestList() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubStructured(repository, List.of(publishedDocument(16L, "Giáo trình hiện có")), 1);
        stubSuggestions(repository, List.of());

        DocumentAssistantResponse response = service.respond("tìm giáo trình tài liệu hiện có");

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(repository).searchPublishedStructured(keyword.capture(), anyString(), anyString(), anyString(),
                isNull(), any(Pageable.class));
        assertEquals("", keyword.getValue());
        assertEquals("LATEST", response.filters().get("intent"));
        assertEquals("newest", response.filters().get("sortMode"));
    }

    @Test
    void parsesEnglishLanguageRequest() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubRelevant(repository, List.of(publishedDocument(17L, "English Java Guide")));
        stubSuggestions(repository, List.of());

        DocumentAssistantResponse response = service.respond("tài liệu tiếng anh");

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> languageCode = ArgumentCaptor.forClass(String.class);
        verify(repository).searchPublishedRelevant(keyword.capture(), anyString(), anyString(),
                languageCode.capture(), isNull(), any(Pageable.class));
        assertEquals("", keyword.getValue());
        assertEquals("en", languageCode.getValue());
        assertEquals("en", response.filters().get("languageCode"));
    }

    @Test
    void parsesPopularRequestAndSortsByCountersWhenSupported() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubStructured(repository, List.of(publishedDocument(20L, "Tài liệu phổ biến")), 1);
        stubSuggestions(repository, List.of());

        DocumentAssistantResponse response = service.respond("Tài liệu nào được xem nhiều?");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchPublishedStructured(anyString(), anyString(), anyString(), anyString(),
                isNull(), pageable.capture());
        assertEquals("POPULAR", response.filters().get("intent"));
        assertEquals("popular", response.filters().get("sortMode"));
        assertTrue(pageable.getValue().getSort().getOrderFor("viewCount").isDescending());
    }

    @Test
    void parsesPublicationYearRequest() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubRelevant(repository, List.of(publishedDocument(30L, "Giáo trình 2025")));
        stubSuggestions(repository, List.of());

        DocumentAssistantResponse response = service.respond("Tìm giáo trình năm 2025");

        ArgumentCaptor<Integer> year = ArgumentCaptor.forClass(Integer.class);
        verify(repository).searchPublishedRelevant(anyString(), anyString(), anyString(), anyString(),
                year.capture(), any(Pageable.class));
        assertEquals(2025, year.getValue());
        assertEquals("2025", response.filters().get("year"));
    }

    @Test
    void appliesNewestFollowUpToPreviousKeyword() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubStructured(repository, List.of(publishedDocument(31L, "Java hiện đại")), 1);

        DocumentAssistantContext context = new DocumentAssistantContext("Java", "", "", "", null,
                "relevant", 0, "Nguyễn Văn A", "Công nghệ phần mềm");
        DocumentAssistantResponse response = service.respond("cái mới nhất thôi", context);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchPublishedStructured(keyword.capture(), anyString(), anyString(), anyString(),
                isNull(), pageable.capture());
        assertEquals("Java", keyword.getValue());
        assertEquals("newest", response.context().sortMode());
        assertTrue(pageable.getValue().getSort().getOrderFor("publishedAt").isDescending());
    }

    @Test
    void findsVietnameseTextWithoutDiacriticsUsingLightweightFallback() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubRelevant(repository, List.of());
        when(repository.findPublishedCandidates(any(Pageable.class)))
                .thenReturn(List.of(publishedDocument(32L, "Nhập môn cơ sở dữ liệu")));

        DocumentAssistantResponse response = service.respond("co so du lieu");

        assertEquals("RESULTS", response.type());
        assertEquals("Nhập môn cơ sở dữ liệu", response.documents().getFirst().title());
    }

    @Test
    void expandsJoinedSpringBootAlias() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubRelevant(repository, List.of(publishedDocument(33L, "Spring Boot thực hành")));

        DocumentAssistantResponse response = service.respond("springboot");

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(repository).searchPublishedRelevant(keyword.capture(), anyString(), anyString(), anyString(),
                isNull(), any(Pageable.class));
        assertEquals("Spring Boot", keyword.getValue());
        assertFalse(response.documents().getFirst().actions().isEmpty());
        assertTrue(response.suggestions().stream().anyMatch(item -> item.contains("mới nhất")));
    }

    @Test
    void toleratesASmallKeywordTypo() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        stubRelevant(repository, List.of());
        when(repository.findPublishedCandidates(any(Pageable.class)))
                .thenReturn(List.of(publishedDocument(34L, "Lập trình web căn bản")));

        DocumentAssistantResponse response = service.respond("lap trinh wep");

        assertEquals("RESULTS", response.type());
        assertEquals("Lập trình web căn bản", response.documents().getFirst().title());
    }

    @Test
    void noMatchReturnsSuggestions() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        DocumentAssistantService service = new DocumentAssistantServiceImpl(repository);
        when(repository.searchPublishedRelevant(anyString(), anyString(), anyString(), anyString(),
                any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(5), 0));
        stubSuggestions(repository, List.of("Vật lý", "Cơ học lượng tử"));

        DocumentAssistantResponse response = service.respond("Tìm tài liệu lượng tử nâng cao");

        assertEquals("NO_RESULTS", response.type());
        assertTrue(response.documents().isEmpty());
        assertFalse(response.hasMore());
        assertTrue(response.message().contains("lượng tử nâng cao"));
        assertTrue(response.suggestions().contains("Tìm tài liệu về Vật lý"));
    }

    private void stubRelevant(DocumentAssistantRepository repository, List<Document> documents) {
        when(repository.searchPublishedRelevant(anyString(), anyString(), anyString(), anyString(),
                any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(documents, invocation.getArgument(5), documents.size()));
    }

    private void stubStructured(DocumentAssistantRepository repository, List<Document> documents, long total) {
        when(repository.searchPublishedStructured(anyString(), anyString(), anyString(), anyString(),
                any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(documents, invocation.getArgument(5), total));
    }

    private void stubSuggestions(DocumentAssistantRepository repository, List<String> suggestions) {
        when(repository.suggestPublishedCategories(anyString(), any(Pageable.class))).thenReturn(suggestions);
    }

    private Document publishedDocument(Long id, String title) {
        Category category = new Category();
        category.setName("Công nghệ phần mềm");
        Document document = new Document();
        document.setId(id);
        document.setTitle(title);
        document.setSummary("Mô tả ngắn cho tài liệu");
        document.setAuthorName("Nguyễn Văn A");
        document.setCategory(category);
        document.setPublishedAt(LocalDateTime.of(2026, 9, 17, 10, 0));
        return document;
    }
}
