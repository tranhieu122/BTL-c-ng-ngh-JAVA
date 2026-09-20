package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.dto.DocumentRatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, Long> {
    Optional<DocumentReview> findByDocumentIdAndUserId(Long documentId, Long userId);
    boolean existsByDocumentIdAndUserId(Long documentId, Long userId);
    List<DocumentReview> findByDocumentIdAndHiddenFalseAndCommentIsNotNullOrderByCreatedAtDesc(Long documentId);
    List<DocumentReview> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = "document")
    List<DocumentReview> findByUserIdOrderByUpdatedAtDesc(Long userId);

    @Query("select coalesce(avg(r.rating), 0) from DocumentReview r where r.document.id = :documentId and r.hidden = false")
    double averageVisibleRating(@Param("documentId") Long documentId);

    long countByDocumentIdAndHiddenFalse(Long documentId);

    @Query("select new com.hieu.edurepo.dto.DocumentRatingSummary(r.document.id, avg(r.rating), count(r.id)) "
            + "from DocumentReview r where r.hidden = false and r.document.id in :documentIds "
            + "group by r.document.id")
    List<DocumentRatingSummary> findVisibleRatingSummaries(@Param("documentIds") Collection<Long> documentIds);
}
