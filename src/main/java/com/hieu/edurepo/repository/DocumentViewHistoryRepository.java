package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentViewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface DocumentViewHistoryRepository extends JpaRepository<DocumentViewHistory, Long> {
    Optional<DocumentViewHistory> findByUserIdAndDocumentId(Long userId, Long documentId);
    @EntityGraph(attributePaths = {"document", "document.category"})
    List<DocumentViewHistory> findByUserIdOrderByViewedAtDesc(Long userId);
}
