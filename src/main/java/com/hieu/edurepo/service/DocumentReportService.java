package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentReportForm;
import com.hieu.edurepo.entity.DocumentReport;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentReportStatus;

import java.util.List;
import java.util.Set;

public interface DocumentReportService {
    DocumentReport create(Long documentId, User reporter, DocumentReportForm form);
    List<DocumentReport> findAllForModeration();
    DocumentReport updateStatus(Long reportId, DocumentReportStatus status, User handler);
    Set<Long> seriouslyReportedDocumentIds();
}
