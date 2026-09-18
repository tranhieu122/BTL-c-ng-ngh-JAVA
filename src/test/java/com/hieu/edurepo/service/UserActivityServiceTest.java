package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentDownloadHistory;
import com.hieu.edurepo.entity.DocumentViewHistory;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.repository.DocumentDownloadHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.repository.DocumentViewHistoryRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.impl.UserActivityServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserActivityServiceTest {
    private final DocumentViewHistoryRepository views = mock(DocumentViewHistoryRepository.class);
    private final DocumentDownloadHistoryRepository downloads = mock(DocumentDownloadHistoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final UserActivityService service = new UserActivityServiceImpl(
            views, downloads, mock(DocumentReviewRepository.class), users, documents);

    @Test
    void firstViewCreatesHistoryAndARepeatedViewUpdatesTheSameRow() {
        User user = new User();
        Document document = new Document();
        when(users.lockById(7L)).thenReturn(Optional.of(user));
        when(documents.getReferenceById(11L)).thenReturn(document);
        when(views.findByUserIdAndDocumentId(7L, 11L)).thenReturn(Optional.empty());

        service.recordView(7L, 11L);

        ArgumentCaptor<DocumentViewHistory> captor = ArgumentCaptor.forClass(DocumentViewHistory.class);
        verify(views).save(captor.capture());
        DocumentViewHistory created = captor.getValue();
        assertSame(user, created.getUser());
        assertSame(document, created.getDocument());
        assertNotNull(created.getViewedAt());

        LocalDateTime oldTime = LocalDateTime.now().minusDays(1);
        created.setViewedAt(oldTime);
        when(views.findByUserIdAndDocumentId(7L, 11L)).thenReturn(Optional.of(created));
        service.recordView(7L, 11L);
        verify(views, times(2)).save(created);
    }

    @Test
    void downloadsAreRecordedOncePerDocumentAndCounted() {
        DocumentDownloadHistory existing = new DocumentDownloadHistory();
        existing.setDownloadCount(3);
        when(users.lockById(7L)).thenReturn(Optional.of(new User()));
        when(downloads.findByUserIdAndDocumentId(7L, 11L)).thenReturn(Optional.of(existing));

        service.recordDownload(7L, 11L);

        assertEquals(4, existing.getDownloadCount());
        assertNotNull(existing.getDownloadedAt());
        verify(downloads).save(existing);
    }
}
