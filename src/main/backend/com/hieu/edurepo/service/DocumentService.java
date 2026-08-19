package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DocumentService {
    Document findById(Long id);
    List<Document> findByOwner(Long userId);
    List<Document> findPendingReview();
    Page<Document> searchPublished(String keyword, Pageable pageable);
    Document saveDraft(Document document, User owner);
    Document submit(Long documentId, User owner);
    Document changeStatus(Long documentId, DocumentStatus status);
    void deleteDraft(Long documentId, User owner);
}
