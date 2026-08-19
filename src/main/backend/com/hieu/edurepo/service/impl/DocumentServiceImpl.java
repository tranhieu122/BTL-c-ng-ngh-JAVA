package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentServiceImpl(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Document findById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findByOwner(Long userId) {
        return documentRepository.findByCreatedByIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findPendingReview() {
        return documentRepository.findByStatusOrderBySubmittedAtAsc(DocumentStatus.SUBMITTED);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> searchPublished(String keyword, Pageable pageable) {
        return documentRepository.findByStatusAndTitleContainingIgnoreCase(
                DocumentStatus.PUBLISHED, keyword == null ? "" : keyword.trim(), pageable);
    }

    @Override
    public Document saveDraft(Document document, User owner) {
        document.setCreatedBy(owner);
        if (document.getStatus() == null) {
            document.setStatus(DocumentStatus.DRAFT);
        }
        return documentRepository.save(document);
    }

    @Override
    public Document submit(Long documentId, User owner) {
        Document document = findOwnedDocument(documentId, owner);
        if (document.getStatus() != DocumentStatus.DRAFT
                && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new InvalidStatusException("Chỉ tài liệu nháp hoặc cần chỉnh sửa mới được gửi duyệt");
        }
        document.setStatus(DocumentStatus.SUBMITTED);
        document.setSubmittedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    @Override
    public Document changeStatus(Long documentId, DocumentStatus status) {
        Document document = findById(documentId);
        document.setStatus(status);
        if (status == DocumentStatus.PUBLISHED) {
            document.setPublishedAt(LocalDateTime.now());
        }
        return documentRepository.save(document);
    }

    @Override
    public void deleteDraft(Long documentId, User owner) {
        Document document = findOwnedDocument(documentId, owner);
        if (document.getStatus() != DocumentStatus.DRAFT) {
            throw new InvalidStatusException("Chỉ được xóa tài liệu đang ở trạng thái nháp");
        }
        documentRepository.delete(document);
    }

    private Document findOwnedDocument(Long documentId, User owner) {
        Document document = findById(documentId);
        if (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(owner.getId())) {
            throw new InvalidStatusException("Bạn không có quyền thay đổi tài liệu này");
        }
        return document;
    }
}
