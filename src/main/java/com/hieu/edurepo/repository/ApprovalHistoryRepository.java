package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Kho lưu trữ dữ liệu lịch sử phê duyệt và kiểm duyệt tài liệu học liệu.
 */
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    @EntityGraph(attributePaths = "reviewer")
    List<ApprovalHistory> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
    @EntityGraph(attributePaths = "reviewer")
    Page<ApprovalHistory> findByDocumentIdOrderByCreatedAtDesc(Long documentId, Pageable pageable);
    boolean existsByReviewerId(Long reviewerId);
}
