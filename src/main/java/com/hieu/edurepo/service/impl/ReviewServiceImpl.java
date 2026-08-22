package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.ReviewAction;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.ApprovalHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.ReviewService;
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

    public ReviewServiceImpl(DocumentService documentService,
                             DocumentRepository documentRepository,
                             ApprovalHistoryRepository historyRepository) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    public Document review(Long documentId, ReviewAction action, String comment, User reviewer) {
        Document document = documentService.findById(documentId);
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
        historyRepository.save(history);
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
}
