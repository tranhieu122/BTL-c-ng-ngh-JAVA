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
import static org.mockito.Mockito.mock;
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
}
