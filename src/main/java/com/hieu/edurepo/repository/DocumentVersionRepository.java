package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);
    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionNumberDesc(Long documentId);
    Optional<DocumentVersion> findByIdAndDocumentId(Long id, Long documentId);
    void deleteByDocumentId(Long documentId);
}
