package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentReport;
import com.hieu.edurepo.enums.DocumentReportReason;
import com.hieu.edurepo.enums.DocumentReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Kho lưu trữ dữ liệu khiếu nại, báo cáo sai phạm học liệu từ cộng đồng người dùng.
 */
public interface DocumentReportRepository extends JpaRepository<DocumentReport, Long> {
    boolean existsByDocumentIdAndReporterIdAndReason(Long documentId, Long reporterId, DocumentReportReason reason);
    List<DocumentReport> findAllByOrderByCreatedAtDesc();

    @Query("select r.document.id from DocumentReport r where r.reason in :reasons and r.status in :statuses "
            + "group by r.document.id having count(r) >= :threshold")
    List<Long> findSeriouslyReportedDocumentIds(@Param("reasons") Collection<DocumentReportReason> reasons,
                                                 @Param("statuses") Collection<DocumentReportStatus> statuses,
                                                 @Param("threshold") long threshold);
}
