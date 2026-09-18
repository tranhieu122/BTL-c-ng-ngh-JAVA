package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentDownloadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface DocumentDownloadHistoryRepository extends JpaRepository<DocumentDownloadHistory, Long> {
    Optional<DocumentDownloadHistory> findByUserIdAndDocumentId(Long userId, Long documentId);
    @EntityGraph(attributePaths = {"document", "document.category"})
    List<DocumentDownloadHistory> findByUserIdOrderByDownloadedAtDesc(Long userId);
}
