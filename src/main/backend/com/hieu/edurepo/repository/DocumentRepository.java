package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByCreatedByIdOrderByCreatedAtDesc(Long userId);
    List<Document> findByStatusOrderBySubmittedAtAsc(DocumentStatus status);
    long countByStatus(DocumentStatus status);
    Page<Document> findByStatusAndTitleContainingIgnoreCase(
            DocumentStatus status, String keyword, Pageable pageable);
}
