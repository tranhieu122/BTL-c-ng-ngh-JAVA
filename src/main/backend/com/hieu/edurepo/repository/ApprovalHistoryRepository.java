package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}
