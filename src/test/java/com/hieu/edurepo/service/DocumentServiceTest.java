package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @Test
    void submitMovesDraftToSubmitted() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.DRAFT);
        when(repository.findById(10L)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);

        Document result = service.submit(10L, owner);

        assertEquals(DocumentStatus.SUBMITTED, result.getStatus());
    }

    @Test
    void submitRejectsAUserWhoDoesNotOwnTheDocument() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        User intruder = new User();
        intruder.setId(2L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.DRAFT);
        when(repository.findById(10L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> service.submit(10L, intruder));
    }

    @Test
    void saveDraftCannotBypassReviewWithCallerSuppliedStatus() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setStatus(DocumentStatus.PUBLISHED);
        when(repository.save(document)).thenReturn(document);

        Document saved = service.saveDraft(document, owner);

        assertEquals(DocumentStatus.DRAFT, saved.getStatus());
        assertEquals(owner, saved.getCreatedBy());
        verify(repository).save(document);
    }

    @Test
    void submitNewAssignsOwnerAndSubmittedStatus() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setStatus(DocumentStatus.PUBLISHED);
        when(repository.save(document)).thenReturn(document);

        Document saved = service.submitNew(document, owner);

        assertEquals(DocumentStatus.SUBMITTED, saved.getStatus());
        assertEquals(owner, saved.getCreatedBy());
        verify(repository).save(document);
    }

    @Test
    void updateDraftPreservesRevisionStatusAndExistingFileWhenNoReplacementIsProvided() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.REVISION_REQUIRED);
        document.setFilePath("old-file.pdf");
        Document changes = new Document();
        changes.setTitle("Tiêu đề mới");
        changes.setDescription("Mô tả mới");
        when(repository.findById(10L)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);

        Document result = service.updateDraft(10L, changes, owner);

        assertEquals(DocumentStatus.REVISION_REQUIRED, result.getStatus());
        assertEquals("Tiêu đề mới", result.getTitle());
        assertEquals("Mô tả mới", result.getDescription());
        assertEquals("old-file.pdf", result.getFilePath());
        verify(repository).save(document);
    }

    @Test
    void updateDraftRejectsDocumentAlreadySubmittedForReview() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.SUBMITTED);
        when(repository.findById(10L)).thenReturn(Optional.of(document));

        assertThrows(InvalidStatusException.class,
                () -> service.updateDraft(10L, new Document(), owner));
    }
}
