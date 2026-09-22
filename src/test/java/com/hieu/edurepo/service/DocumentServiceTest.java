package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentVersion;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentVersionRepository;
import com.hieu.edurepo.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử nghiệp vụ quản lý tài liệu (Document Service Test).
 * Xác minh các chức năng tạo bài, lọc tài liệu công khai, tìm kiếm theo bộ môn và tăng biến đếm lượt tải.
 */
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
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(document));
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
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(document));

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
    void reviewerSubmissionMustStillEnterWorkflow() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User reviewer = new User();
        reviewer.setId(1L);
        reviewer.setRoles(Set.of(new Role(RoleName.REVIEWER)));
        Document document = new Document();
        when(repository.save(document)).thenReturn(document);

        Document saved = service.submitNew(document, reviewer);

        assertEquals(DocumentStatus.SUBMITTED, saved.getStatus());
        assertEquals(reviewer, saved.getCreatedBy());
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
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(document));
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
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(document));

        assertThrows(InvalidStatusException.class,
                () -> service.updateDraft(10L, new Document(), owner));
    }

    @Test
    void replacingFileCreatesNextVersionAndKeepsChecksum() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        FileStorageService storageService = mock(FileStorageService.class);
        DocumentService service = new DocumentServiceImpl(repository, versionRepository, storageService);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.DRAFT);
        DocumentVersion previous = new DocumentVersion();
        previous.setVersionNumber(1);
        Document changes = new Document();
        changes.setTitle("Bản hai");
        changes.setFileName("lecture-v2.pdf");
        changes.setFilePath("stored-v2.pdf");
        changes.setFileType("application/pdf");
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);
        when(versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(10L)).thenReturn(Optional.of(previous));
        when(storageService.checksum("stored-v2.pdf")).thenReturn("abc123");

        service.updateDraft(10L, changes, owner);

        ArgumentCaptor<DocumentVersion> captor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionRepository).save(captor.capture());
        verify(versionRepository).clearCurrentVersion(10L);
        assertEquals(2, captor.getValue().getVersionNumber());
        assertEquals("stored-v2.pdf", captor.getValue().getFilePath());
        assertEquals("abc123", captor.getValue().getChecksum());
        assertEquals(true, captor.getValue().isCurrentVersion());
    }

    @Test
    void resubmitMovesRevisionRequiredToResubmitted() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.REVISION_REQUIRED);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);

        Document result = service.submit(10L, owner);

        assertEquals(DocumentStatus.RESUBMITTED, result.getStatus());
    }

    @Test
    void deleteDraftDeletesPhysicalFilesOnDisk() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
        FileStorageService storageService = mock(FileStorageService.class);
        DocumentService service = new DocumentServiceImpl(repository, versionRepository, storageService);

        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.DRAFT);
        document.setFilePath("draft-main.pdf");

        DocumentVersion v1 = new DocumentVersion();
        v1.setFilePath("draft-v1.pdf");
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(document));
        when(versionRepository.findByDocumentIdOrderByVersionNumberDesc(10L)).thenReturn(java.util.List.of(v1));

        service.deleteDraft(10L, owner);

        verify(repository).delete(document);
        verify(versionRepository).deleteByDocumentId(10L);
        verify(storageService).delete("draft-main.pdf");
        verify(storageService).delete("draft-v1.pdf");
    }
}
