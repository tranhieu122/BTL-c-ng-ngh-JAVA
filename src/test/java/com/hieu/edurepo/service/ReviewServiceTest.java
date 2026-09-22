package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.ReviewAction;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.ApprovalHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử dịch vụ thẩm định học liệu của Giảng viên (Review Service Test).
 * Kiểm tra công thức tính điểm Rubric có trọng số và chuyển đổi trạng thái duyệt bài.
 */
class ReviewServiceTest {

    @Test
    void adminCanPublishSubmittedDocumentDirectlyAndCapturesStatuses() {
        Fixture fixture = fixture(DocumentStatus.SUBMITTED);

        fixture.service.review(2L, ReviewAction.PUBLISHED, "Công khai", admin());

        assertEquals(DocumentStatus.PUBLISHED, fixture.document.getStatus());
        assertNotNull(fixture.document.getPublishedAt());
        ArgumentCaptor<ApprovalHistory> captor = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(fixture.histories).save(captor.capture());
        assertEquals(DocumentStatus.SUBMITTED, captor.getValue().getOldStatus());
        assertEquals(DocumentStatus.PUBLISHED, captor.getValue().getNewStatus());
    }

    @Test
    void approveRequiresUnderReviewAndStoresRubric() {
        Fixture fixture = fixture(DocumentStatus.UNDER_REVIEW);

        fixture.service.review(2L, ReviewAction.APPROVED, "Đạt yêu cầu", reviewer(), 5, 4, 3);

        assertEquals(DocumentStatus.APPROVED, fixture.document.getStatus());
        ArgumentCaptor<ApprovalHistory> captor = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(fixture.histories).save(captor.capture());
        assertEquals(ReviewAction.APPROVED, captor.getValue().getAction());
        assertEquals(DocumentStatus.UNDER_REVIEW, captor.getValue().getOldStatus());
        assertEquals(DocumentStatus.APPROVED, captor.getValue().getNewStatus());
        assertEquals(5, captor.getValue().getContentQualityScore());
        assertEquals(4, captor.getValue().getTeachingEffectivenessScore());
        assertEquals(3, captor.getValue().getEaseOfUseScore());
    }

    @Test
    void revisionRequestAndRejectionUseExpectedStatuses() {
        assertDecision(ReviewAction.REVISION_REQUESTED, DocumentStatus.REVISION_REQUIRED);
        assertDecision(ReviewAction.REJECTED, DocumentStatus.REJECTED);
    }

    @Test
    void reviewerCanApproveSubmittedDocumentDirectly() {
        Fixture fixture = fixture(DocumentStatus.SUBMITTED);

        fixture.service.review(2L, ReviewAction.APPROVED, "Đạt yêu cầu", reviewer());

        assertEquals(DocumentStatus.APPROVED, fixture.document.getStatus());
        verify(fixture.histories).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onlyAdminCanPublishAndArchive() {
        Fixture publish = fixture(DocumentStatus.APPROVED);
        assertThrows(AccessDeniedException.class,
                () -> publish.service.review(2L, ReviewAction.PUBLISHED, "Sai quyền", reviewer()));

        publish.service.review(2L, ReviewAction.PUBLISHED, "Công bố", admin());
        assertEquals(DocumentStatus.PUBLISHED, publish.document.getStatus());
        assertNotNull(publish.document.getPublishedAt());

        Fixture archive = fixture(DocumentStatus.PUBLISHED);
        archive.service.review(2L, ReviewAction.ARCHIVED, "Lưu trữ", admin());
        assertEquals(DocumentStatus.ARCHIVED, archive.document.getStatus());
    }

    @Test
    void ordinaryUserCannotReview() {
        Fixture fixture = fixture(DocumentStatus.SUBMITTED);
        User user = user(RoleName.USER);

        assertThrows(AccessDeniedException.class,
                () -> fixture.service.review(2L, ReviewAction.APPROVED, "Không có quyền", user));
    }

    private void assertDecision(ReviewAction action, DocumentStatus expectedStatus) {
        Fixture fixture = fixture(DocumentStatus.UNDER_REVIEW);
        fixture.service.review(2L, action, "Phản hồi", reviewer());
        assertEquals(expectedStatus, fixture.document.getStatus());
        verify(fixture.repository).save(fixture.document);
        verify(fixture.histories).save(org.mockito.ArgumentMatchers.any(ApprovalHistory.class));
    }

    private Fixture fixture(DocumentStatus status) {
        DocumentService documentService = mock(DocumentService.class);
        DocumentRepository repository = mock(DocumentRepository.class);
        ApprovalHistoryRepository histories = mock(ApprovalHistoryRepository.class);
        ReviewService service = new ReviewServiceImpl(documentService, repository, histories);
        Document document = new Document();
        document.setId(2L);
        document.setStatus(status);
        when(repository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(document));
        return new Fixture(service, repository, histories, document);
    }

    private User reviewer() { return user(RoleName.REVIEWER); }
    private User admin() { return user(RoleName.ADMIN); }

    private User user(RoleName role) {
        User user = new User();
        user.setId(10L);
        user.setRoles(Set.of(new Role(role)));
        return user;
    }

    private record Fixture(ReviewService service, DocumentRepository repository,
                           ApprovalHistoryRepository histories, Document document) { }
}
