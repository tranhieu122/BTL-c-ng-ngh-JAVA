package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    @EntityGraph(attributePaths = "createdBy")
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);
    @EntityGraph(attributePaths = "createdBy")
    Page<DocumentVersion> findPageByDocumentIdOrderByVersionNumberDesc(Long documentId, Pageable pageable);
    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionNumberDesc(Long documentId);
    Optional<DocumentVersion> findByIdAndDocumentId(Long id, Long documentId);
    Optional<DocumentVersion> findByDocumentIdAndCurrentVersionTrue(Long documentId);
    @Modifying(flushAutomatically = true)
    @Query("update DocumentVersion v set v.currentVersion = false where v.document.id = :documentId and v.currentVersion = true")
    int clearCurrentVersion(@Param("documentId") Long documentId);
    void deleteByDocumentId(Long documentId);
}
