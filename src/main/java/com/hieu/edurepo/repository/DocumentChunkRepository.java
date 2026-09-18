package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    long countByDocumentId(Long documentId);

    @Query("SELECT c FROM DocumentChunk c JOIN FETCH c.document d WHERE d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED")
    List<DocumentChunk> findAllPublishedChunks();

    @Query("SELECT c FROM DocumentChunk c JOIN FETCH c.document d WHERE d.id IN :documentIds AND d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED")
    List<DocumentChunk> findPublishedChunksByDocumentIds(@Param("documentIds") Collection<Long> documentIds);
}
