package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.ReviewAction;
import com.hieu.edurepo.enums.NotificationType;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.ApprovalHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.ReviewService;
import com.hieu.edurepo.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final NotificationService notificationService;

    public ReviewServiceImpl(DocumentService documentService,
                             DocumentRepository documentRepository,
                             ApprovalHistoryRepository historyRepository) {
        this(documentService, documentRepository, historyRepository, null);
    }

    @Autowired
    public ReviewServiceImpl(DocumentService documentService,
                             DocumentRepository documentRepository,
                             ApprovalHistoryRepository historyRepository,
                             NotificationService notificationService) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
    }

    @Override
    public Document review(Long documentId, ReviewAction action, String comment, User reviewer) {
        return review(documentId, action, comment, reviewer, null, null, null);
    }

    @Override
    public Document review(Long documentId, ReviewAction action, String comment, User reviewer,
                           Integer contentQualityScore, Integer teachingEffectivenessScore,
                           Integer easeOfUseScore) {
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new com.hieu.edurepo.exception.ResourceNotFoundException("Không tìm thấy tài liệu"));
        DocumentStatus nextStatus = resolveNextStatus(document.getStatus(), action);
        document.setStatus(nextStatus);
        if (nextStatus == DocumentStatus.PUBLISHED) {
            document.setPublishedAt(LocalDateTime.now());
        }
        documentRepository.save(document);

        ApprovalHistory history = new ApprovalHistory();
        history.setDocument(document);
        history.setReviewer(reviewer);
        history.setAction(action);
        history.setComment(comment);
        history.setContentQualityScore(contentQualityScore);
        history.setTeachingEffectivenessScore(teachingEffectivenessScore);
        history.setEaseOfUseScore(easeOfUseScore);
        history = historyRepository.save(history);
        if (notificationService != null) {
            notificationService.reviewed(document, notificationType(action),
                    "review:" + documentId + ":" + action + ":" + history.getId());
        }
        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalHistory> history(Long documentId) {
        documentService.findById(documentId);
        return historyRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
    }

    private DocumentStatus resolveNextStatus(DocumentStatus current, ReviewAction action) {
        if (action == null) {
            throw new InvalidStatusException("Thao tác duyệt không hợp lệ");
        }
        if (current == DocumentStatus.SUBMITTED) {
            return switch (action) {
                case APPROVED -> DocumentStatus.APPROVED;
                case REJECTED -> DocumentStatus.REJECTED;
                case REVISION_REQUESTED -> DocumentStatus.REVISION_REQUIRED;
                default -> throw new InvalidStatusException("Thao tác duyệt không hợp lệ");
            };
        }
        if (current == DocumentStatus.APPROVED && action == ReviewAction.PUBLISHED) {
            return DocumentStatus.PUBLISHED;
        }
        throw new InvalidStatusException("Không thể thực hiện thao tác với trạng thái hiện tại");
    }

    private NotificationType notificationType(ReviewAction action) {
        return switch (action) {
            case APPROVED -> NotificationType.DOCUMENT_APPROVED;
            case REJECTED -> NotificationType.DOCUMENT_REJECTED;
            case REVISION_REQUESTED -> NotificationType.DOCUMENT_REVISION_REQUIRED;
            case PUBLISHED -> NotificationType.DOCUMENT_PUBLISHED;
            case SUBMITTED -> throw new InvalidStatusException("Thao tác duyệt không hợp lệ");
        };
    }
}
