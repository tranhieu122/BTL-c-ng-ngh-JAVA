package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.SubmitterRequest;
import com.hieu.edurepo.enums.SubmitterRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ yêu cầu đăng ký nâng quyền Người đăng tải học liệu (Submitter).
 */
public interface SubmitterRequestRepository extends JpaRepository<SubmitterRequest, Long> {
    boolean existsByRequesterIdAndStatus(Long requesterId, SubmitterRequestStatus status);
    @EntityGraph(attributePaths = {"requester", "reviewedBy"})
    List<SubmitterRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    @EntityGraph(attributePaths = {"requester", "reviewedBy"})
    List<SubmitterRequest> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SubmitterRequest r where r.id = :id")
    Optional<SubmitterRequest> lockById(@Param("id") Long id);
}
