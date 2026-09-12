package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.dto.DocumentReportForm;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentReport;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentReportReason;
import com.hieu.edurepo.enums.DocumentReportStatus;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.DocumentReportRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentReportService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class DocumentReportServiceImpl implements DocumentReportService {
    public static final long SERIOUS_REPORT_WARNING_THRESHOLD = 3;

    private final DocumentReportRepository reportRepository;
    private final DocumentRepository documentRepository;

    public DocumentReportServiceImpl(DocumentReportRepository reportRepository, DocumentRepository documentRepository) {
        this.reportRepository = reportRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public DocumentReport create(Long documentId, User reporter, DocumentReportForm form) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu"));
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new InvalidStatusException("Chỉ tài liệu đã công bố mới có thể được báo cáo.");
        }
        validate(form);
        if (reportRepository.existsByDocumentIdAndReporterIdAndReason(documentId, reporter.getId(), form.getReason())) {
            throw new InvalidStatusException("Bạn đã báo cáo tài liệu này với cùng lý do.");
        }
        DocumentReport report = new DocumentReport();
        report.setDocument(document);
        report.setReporter(reporter);
        report.setReason(form.getReason());
        report.setDescription(normalize(form.getDescription()));
        try {
            return reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidStatusException("Bạn đã báo cáo tài liệu này với cùng lý do.");
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @Transactional(readOnly = true)
    public List<DocumentReport> findAllForModeration() {
        return reportRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public DocumentReport updateStatus(Long reportId, DocumentReportStatus status, User handler) {
        if (status == null) throw new IllegalArgumentException("Trạng thái xử lý không hợp lệ");
        DocumentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo"));
        report.setStatus(status);
        report.setHandledBy(handler);
        report.setHandledAt(status == DocumentReportStatus.PENDING ? null : LocalDateTime.now());
        return reportRepository.save(report);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @Transactional(readOnly = true)
    public Set<Long> seriouslyReportedDocumentIds() {
        Set<DocumentReportReason> reasons = EnumSet.allOf(DocumentReportReason.class);
        reasons.removeIf(reason -> !reason.isSerious());
        return new HashSet<>(reportRepository.findSeriouslyReportedDocumentIds(reasons,
                EnumSet.of(DocumentReportStatus.PENDING, DocumentReportStatus.UNDER_REVIEW),
                SERIOUS_REPORT_WARNING_THRESHOLD));
    }

    private void validate(DocumentReportForm form) {
        if (form == null || form.getReason() == null) throw new IllegalArgumentException("Vui lòng chọn lý do báo cáo");
        if (form.getDescription() != null && form.getDescription().length() > 1000) {
            throw new IllegalArgumentException("Mô tả không được vượt quá 1000 ký tự");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
