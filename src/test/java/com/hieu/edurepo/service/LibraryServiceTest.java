package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Bookmark;
import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentCollection;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.BookmarkRepository;
import com.hieu.edurepo.repository.CollectionItemRepository;
import com.hieu.edurepo.repository.DocumentCollectionRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.impl.LibraryServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryServiceTest {

    @Test
    void publishedDocumentCanBeBookmarked() {
        Fixture fixture = new Fixture();
        User user = new User();
        user.setId(7L);
        Document document = fixture.document(DocumentStatus.PUBLISHED);
        when(fixture.documentService.findById(11L)).thenReturn(document);
        when(fixture.bookmarkRepository.findByUserIdAndDocumentId(7L, 11L)).thenReturn(Optional.empty());

        assertTrue(fixture.service.toggleBookmark(user, 11L));

        ArgumentCaptor<Bookmark> captor = ArgumentCaptor.forClass(Bookmark.class);
        verify(fixture.bookmarkRepository).save(captor.capture());
        assertTrue(captor.getValue().getDocument() == document);
    }

    @Test
    void privateDocumentCannotLeakIntoBookmarks() {
        Fixture fixture = new Fixture();
        User user = new User();
        user.setId(7L);
        when(fixture.documentService.findById(11L)).thenReturn(fixture.document(DocumentStatus.DRAFT));

        assertThrows(ResourceNotFoundException.class, () -> fixture.service.toggleBookmark(user, 11L));
        verify(fixture.bookmarkRepository, never()).save(any());
    }

    @Test
    void togglingExistingBookmarkRemovesIt() {
        Fixture fixture = new Fixture();
        User user = new User();
        user.setId(7L);
        Bookmark bookmark = new Bookmark();
        when(fixture.documentService.findById(11L)).thenReturn(fixture.document(DocumentStatus.PUBLISHED));
        when(fixture.bookmarkRepository.findByUserIdAndDocumentId(7L, 11L)).thenReturn(Optional.of(bookmark));

        assertFalse(fixture.service.toggleBookmark(user, 11L));
        verify(fixture.bookmarkRepository).delete(bookmark);
    }

    @Test
    void recommendationsUseOnlySelectedCategoriesAndExcludeCurrentDocument() {
        Fixture fixture = new Fixture();
        Document current = fixture.document(DocumentStatus.PUBLISHED);
        current.setId(11L);
        Category category = new Category();
        category.setId(3L);
        current.setCategory(category);

        fixture.service.recommend(null, current, 4);

        verify(fixture.documentRepository).findRecommendedByCategories(eq(List.of(3L)), eq(11L), any());
    }

    private static class Fixture {
        final BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        final DocumentCollectionRepository collectionRepository = mock(DocumentCollectionRepository.class);
        final CollectionItemRepository itemRepository = mock(CollectionItemRepository.class);
        final DocumentRepository documentRepository = mock(DocumentRepository.class);
        final DocumentService documentService = mock(DocumentService.class);
        final LibraryService service = new LibraryServiceImpl(bookmarkRepository, collectionRepository,
                itemRepository, documentRepository, documentService);

        Document document(DocumentStatus status) {
            Document document = new Document();
            document.setId(11L);
            document.setStatus(status);
            return document;
        }
    }
}
