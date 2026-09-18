package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.ReviewAction;
import com.hieu.edurepo.enums.NotificationType;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.ApprovalHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.ReviewService;
import com.hieu.edurepo.service.NotificationService;
import com.hieu.edurepo.service.AuditLogService;
import com.hieu.edurepo.service.RealtimeChangeTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogs;
    private final RealtimeChangeTracker realtimeChanges;
    private final com.hieu.edurepo.service.DocumentIndexingService documentIndexingService;

    public ReviewServiceImpl(DocumentService documentService,
                             DocumentRepository documentRepository,
                             ApprovalHistoryRepository historyRepository) {
        this(documentService, documentRepository, historyRepository, null, null, null, null);
    }

    @Autowired
    public ReviewServiceImpl(DocumentService documentService,
                             DocumentRepository documentRepository,
                             ApprovalHistoryRepository historyRepository,
                             NotificationService notificationService,
                             AuditLogService auditLogs,
                             RealtimeChangeTracker realtimeChanges,
                             @Autowired(required = false) com.hieu.edurepo.service.DocumentIndexingService documentIndexingService) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.auditLogs = auditLogs;
        this.realtimeChanges = realtimeChanges;
        this.documentIndexingService = documentIndexingService;
    }

    @Override
    public Document review(Long documentId, ReviewAction action, String comment, User reviewer) {
        return review(documentId, action, comment, reviewer, null, null, null);
    }

    @Override
    public Document review(Long documentId, ReviewAction action, String comment, User reviewer,
                           Integer contentQualityScore, Integer teachingEffectivenessScore,
                           Integer easeOfUseScore) {
        // Khóa bản ghi tài liệu trong transaction để hai reviewer không thể ghi hai quyết định trái ngược cùng lúc.
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new com.hieu.edurepo.exception.ResourceNotFoundException("Không tìm thấy tài liệu"));
        requirePermission(action, reviewer);
        DocumentStatus oldStatus = document.getStatus();
        DocumentStatus nextStatus = resolveNextStatus(oldStatus, action);
        document.setStatus(nextStatus);
        if (nextStatus == DocumentStatus.PUBLISHED) {
            document.setPublishedAt(LocalDateTime.now());
        }
        documentRepository.save(document);

        if (documentIndexingService != null) {
            if (nextStatus == DocumentStatus.PUBLISHED) {
                try {
                    documentIndexingService.indexDocument(document);
                } catch (Exception e) {
                    // Tránh lỗi indexing làm gián đoạn transaction phê duyệt của reviewer
                }
            } else {
                try {
                    documentIndexingService.removeIndex(document.getId());
                } catch (Exception e) {
                }
            }
        }

        // Lịch sử duyệt được ghi tách riêng để sau này xem lại ai duyệt, duyệt lúc nào và nhận xét gì.
        ApprovalHistory history = new ApprovalHistory();
        history.setDocument(document);
        history.setReviewer(reviewer);
        history.setAction(action);
        history.setComment(comment);
        history.setOldStatus(oldStatus);
        history.setNewStatus(nextStatus);
        history.setContentQualityScore(contentQualityScore);
        history.setTeachingEffectivenessScore(teachingEffectivenessScore);
        history.setEaseOfUseScore(easeOfUseScore);
        history = historyRepository.save(history);
        if (auditLogs != null) {
            auditLogs.recordTransactionalAsUser(reviewer, auditAction(action), AuditTargetType.DOCUMENT,
                    document.getId(), document.getTitle(),
                    auditAction(action).getLabel() + ": " + document.getTitle(), AuditResult.SUCCESS);
        }
        NotificationType notificationType = notificationType(action);
        if (notificationService != null && notificationType != null) {
            notificationService.reviewed(document, notificationType,
                    "workflow:" + documentId + ":" + action + ":" + history.getId());
        }
        if (realtimeChanges != null) realtimeChanges.documentChangedAfterCommit(
                document.getCreatedBy() == null ? null : document.getCreatedBy().getId());
        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalHistory> history(Long documentId) {
        documentService.findById(documentId);
        return historyRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApprovalHistory> history(Long documentId, Pageable pageable) {
        documentService.findById(documentId);
        return historyRepository.findByDocumentIdOrderByCreatedAtDesc(documentId, pageable);
    }

    private DocumentStatus resolveNextStatus(DocumentStatus current, ReviewAction action) {
        if (action == null) {
            throw new InvalidStatusException("Thao tác duyệt không hợp lệ");
        }
        if (current == DocumentStatus.SUBMITTED || current == DocumentStatus.RESUBMITTED) {
            return switch (action) {
                case APPROVED -> DocumentStatus.APPROVED;
                case REJECTED -> DocumentStatus.REJECTED;
                case REVISION_REQUESTED -> DocumentStatus.REVISION_REQUIRED;
                case PUBLISHED -> DocumentStatus.PUBLISHED;
                default -> throw new InvalidStatusException("Thao tác duyệt không hợp lệ");
            };
        }
        if (current == DocumentStatus.UNDER_REVIEW) {
            return switch (action) {
                case APPROVED -> DocumentStatus.APPROVED;
                case REJECTED -> DocumentStatus.REJECTED;
                case REVISION_REQUESTED -> DocumentStatus.REVISION_REQUIRED;
                default -> throw new InvalidStatusException("Thao tác duyệt không hợp lệ");
            };
        }
        // Sau khi APPROVED, reviewer/admin cần thêm bước PUBLISHED để công bố ra kho công khai.
        if (current == DocumentStatus.APPROVED && action == ReviewAction.PUBLISHED) {
            return DocumentStatus.PUBLISHED;
        }
        if (current == DocumentStatus.PUBLISHED && action == ReviewAction.ARCHIVED) {
            return DocumentStatus.ARCHIVED;
        }
        throw new InvalidStatusException("Không thể thực hiện thao tác với trạng thái hiện tại");
    }

    private NotificationType notificationType(ReviewAction action) {
        return switch (action) {
            case START_REVIEW -> NotificationType.DOCUMENT_UNDER_REVIEW;
            case APPROVED -> NotificationType.DOCUMENT_APPROVED;
            case REJECTED -> NotificationType.DOCUMENT_REJECTED;
            case REVISION_REQUESTED -> NotificationType.DOCUMENT_REVISION_REQUIRED;
            case PUBLISHED -> NotificationType.DOCUMENT_PUBLISHED;
            case SUBMITTED, RESUBMITTED, ARCHIVED -> null;
        };
    }

    private AuditAction auditAction(ReviewAction action) {
        return switch (action) {
            case START_REVIEW -> AuditAction.DOCUMENT_REVIEW_STARTED;
            case APPROVED -> AuditAction.DOCUMENT_APPROVED;
            case REJECTED -> AuditAction.DOCUMENT_REJECTED;
            case REVISION_REQUESTED -> AuditAction.DOCUMENT_REVISION_REQUESTED;
            case PUBLISHED -> AuditAction.DOCUMENT_PUBLISHED;
            case ARCHIVED -> AuditAction.DOCUMENT_ARCHIVED;
            case SUBMITTED -> AuditAction.DOCUMENT_SUBMITTED;
            case RESUBMITTED -> AuditAction.DOCUMENT_RESUBMITTED;
        };
    }

    private void requirePermission(ReviewAction action, User actor) {
        if (action == null) throw new InvalidStatusException("Thao tác duyệt không hợp lệ");
        boolean reviewer = hasRole(actor, RoleName.REVIEWER);
        boolean admin = hasRole(actor, RoleName.ADMIN);
        if (action == ReviewAction.PUBLISHED || action == ReviewAction.ARCHIVED) {
            if (!admin) throw new AccessDeniedException("Chỉ quản trị viên được công bố hoặc lưu trữ tài liệu");
            return;
        }
        if (!reviewer && !admin) {
            throw new AccessDeniedException("Bạn không có quyền kiểm duyệt tài liệu");
        }
    }

    private boolean hasRole(User actor, RoleName role) {
        return actor != null && actor.getRoles().stream().anyMatch(item -> item.getName() == role);
    }
}
