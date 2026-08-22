package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.ReviewAction;
import com.hieu.edurepo.repository.ApprovalHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    @Test
    void approveSubmittedDocumentCreatesHistory() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ApprovalHistoryRepository historyRepository = mock(ApprovalHistoryRepository.class);
        ReviewService service = new ReviewServiceImpl(
                documentService, documentRepository, historyRepository);
        Document document = new Document();
        document.setStatus(DocumentStatus.SUBMITTED);
        User reviewer = new User();
        when(documentService.findById(2L)).thenReturn(document);

        service.review(2L, ReviewAction.APPROVED, "Đạt yêu cầu", reviewer);

        assertEquals(DocumentStatus.APPROVED, document.getStatus());
        ArgumentCaptor<ApprovalHistory> captor = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(ReviewAction.APPROVED, captor.getValue().getAction());
    }

    @Test
    void revisionRequestAndRejectionUseExpectedStatuses() {
        assertTransition(ReviewAction.REVISION_REQUESTED, DocumentStatus.REVISION_REQUIRED);
        assertTransition(ReviewAction.REJECTED, DocumentStatus.REJECTED);
    }

    @Test
    void publishingApprovedDocumentSetsTimestampAndHistory() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ApprovalHistoryRepository historyRepository = mock(ApprovalHistoryRepository.class);
        ReviewService service = new ReviewServiceImpl(
                documentService, documentRepository, historyRepository);
        Document document = new Document();
        document.setStatus(DocumentStatus.APPROVED);
        User reviewer = new User();
        when(documentService.findById(3L)).thenReturn(document);

        service.review(3L, ReviewAction.PUBLISHED, "Công bố", reviewer);

        assertEquals(DocumentStatus.PUBLISHED, document.getStatus());
        assertNotNull(document.getPublishedAt());
        ArgumentCaptor<ApprovalHistory> captor = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(ReviewAction.PUBLISHED, captor.getValue().getAction());
    }

    @Test
    void nullOrInvalidReviewActionReturnsDomainErrorWithoutWritingHistory() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ApprovalHistoryRepository historyRepository = mock(ApprovalHistoryRepository.class);
        ReviewService service = new ReviewServiceImpl(
                documentService, documentRepository, historyRepository);
        Document document = new Document();
        document.setStatus(DocumentStatus.SUBMITTED);
        when(documentService.findById(4L)).thenReturn(document);

        assertThrows(com.hieu.edurepo.exception.InvalidStatusException.class,
                () -> service.review(4L, null, "Không hợp lệ", new User()));
        assertThrows(com.hieu.edurepo.exception.InvalidStatusException.class,
                () -> service.review(4L, ReviewAction.PUBLISHED, "Sai bước", new User()));
        verify(historyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void assertTransition(ReviewAction action, DocumentStatus expectedStatus) {
        DocumentService documentService = mock(DocumentService.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        ApprovalHistoryRepository historyRepository = mock(ApprovalHistoryRepository.class);
        ReviewService service = new ReviewServiceImpl(
                documentService, documentRepository, historyRepository);
        Document document = new Document();
        document.setStatus(DocumentStatus.SUBMITTED);
        when(documentService.findById(5L)).thenReturn(document);

        service.review(5L, action, "Phản hồi", new User());

        assertEquals(expectedStatus, document.getStatus());
        verify(documentRepository).save(document);
        verify(historyRepository).save(org.mockito.ArgumentMatchers.any(ApprovalHistory.class));
    }
}
