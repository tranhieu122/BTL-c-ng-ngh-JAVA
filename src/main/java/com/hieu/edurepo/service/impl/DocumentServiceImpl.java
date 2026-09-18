package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentVersion;
import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.LicenseType;
import com.hieu.edurepo.enums.ReviewAction;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentVersionRepository;
import com.hieu.edurepo.repository.ApprovalHistoryRepository;
import com.hieu.edurepo.service.AuditLogService;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.NotificationService;
import com.hieu.edurepo.service.RealtimeChangeTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private static final List<DocumentStatus> REVIEW_QUEUE_STATUSES = List.of(
            DocumentStatus.SUBMITTED, DocumentStatus.RESUBMITTED,
            DocumentStatus.UNDER_REVIEW, DocumentStatus.APPROVED);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final ApprovalHistoryRepository historyRepository;
    private final AuditLogService auditLogs;
    private final RealtimeChangeTracker realtimeChanges;

    public DocumentServiceImpl(DocumentRepository documentRepository) {
        this(documentRepository, null, null, null, null, null, null);
    }

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentVersionRepository versionRepository,
                               FileStorageService fileStorageService) {
        this(documentRepository, versionRepository, fileStorageService, null, null, null, null);
    }

    @Autowired
    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentVersionRepository versionRepository,
                               FileStorageService fileStorageService,
                               NotificationService notificationService,
                               ApprovalHistoryRepository historyRepository,
                               AuditLogService auditLogs,
                               RealtimeChangeTracker realtimeChanges) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.historyRepository = historyRepository;
        this.auditLogs = auditLogs;
        this.realtimeChanges = realtimeChanges;
    }

    @Override
    @Transactional(readOnly = true)
    public Document findById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findByOwner(Long userId) {
        return documentRepository.findByCreatedByIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> searchByOwner(Long userId, String keyword, DocumentStatus status, Long categoryId,
                                        Long facultyId, Long departmentId, Pageable pageable) {
        return documentRepository.searchByOwner(userId, keyword == null ? "" : keyword.trim(), status,
                categoryId, facultyId, departmentId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findPendingReview() {
        return documentRepository.findByStatusInOrderBySubmittedAtAsc(REVIEW_QUEUE_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> searchPendingReview(String keyword, DocumentStatus status, Pageable pageable) {
        DocumentStatus safeStatus = status != null && REVIEW_QUEUE_STATUSES.contains(status) ? status : null;
        return documentRepository.searchReviewQueue(REVIEW_QUEUE_STATUSES, normalize(keyword), safeStatus, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingReview(DocumentStatus status) {
        return status != null && REVIEW_QUEUE_STATUSES.contains(status)
                ? documentRepository.countByStatus(status) : 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> searchPublished(String keyword, Pageable pageable) {
        return searchPublished(keyword, null, null, null, null, "", pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Document> searchPublished(String keyword, Long categoryId, LearningResourceType resourceType,
                                          EducationLevel educationLevel, LicenseType licenseType,
                                          String languageCode, Pageable pageable) {
        return documentRepository.searchPublishedAdvanced(normalize(keyword), categoryId, resourceType,
                educationLevel, licenseType, normalize(languageCode), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findRelatedPublished(Document document, int limit) {
        if (document == null || document.getId() == null || document.getCategory() == null) return List.of();
        return documentRepository.findRelatedPublished(document.getId(), document.getCategory().getId(),
                PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentVersion> findVersions(Long documentId) {
        findById(documentId);
        return versionRepository == null ? List.of()
                : versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentVersion> findVersions(Long documentId, Pageable pageable) {
        findById(documentId);
        return versionRepository == null ? Page.empty(pageable)
                : versionRepository.findPageByDocumentIdOrderByVersionNumberDesc(documentId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentVersion findVersion(Long documentId, Long versionId) {
        findById(documentId);
        if (versionRepository == null) throw new ResourceNotFoundException("Không tìm thấy phiên bản tài liệu");
        return versionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên bản tài liệu"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> versionFilePaths(Long documentId) {
        return findVersions(documentId).stream().map(DocumentVersion::getFilePath).distinct().toList();
    }

    @Override
    public void recordView(Long documentId) {
        documentRepository.incrementViewCount(documentId);
    }

    @Override
    public void recordDownload(Long documentId) {
        documentRepository.incrementDownloadCount(documentId);
    }

    @Override
    public Document saveDraft(Document document, User owner) {
        // Bản nháp chưa vào kho công khai và chưa vào hàng chờ reviewer.
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.DRAFT);
        Document saved = documentRepository.save(document);
        recordVersion(saved, owner, "Phiên bản ban đầu");
        realtimeChanged(owner.getId());
        return saved;
    }

    @Override
    public Document submitNew(Document document, User owner) {
        document.setCreatedBy(owner);
        // Mọi tài liệu đều đi qua workflow; vai trò cao hơn không được bỏ qua bước kiểm duyệt.
        document.setStatus(DocumentStatus.SUBMITTED);
        document.setSubmittedAt(LocalDateTime.now());
        Document saved = documentRepository.save(document);
        recordVersion(saved, owner, "Phiên bản ban đầu");
        recordTransition(saved, owner, ReviewAction.SUBMITTED, DocumentStatus.DRAFT,
                DocumentStatus.SUBMITTED, "Gửi tài liệu để kiểm duyệt");
        auditWorkflow(owner, AuditAction.DOCUMENT_SUBMITTED, saved, "Gửi tài liệu để duyệt");
        if (notificationService != null) notificationService.submitted(saved);
        realtimeChanged(owner.getId());
        return saved;
    }

    @Override
    public Document updateDraft(Long documentId, Document changes, User owner) {
        return updateDraft(documentId, changes, owner, null);
    }

    @Override
    public Document updateDraft(Long documentId, Document changes, User owner, String changeNote) {
        Document document = findOwnedDocument(documentId, owner);
        if (document.getStatus() != DocumentStatus.DRAFT
                && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new InvalidStatusException("Chỉ tài liệu nháp hoặc cần chỉnh sửa mới được cập nhật");
        }

        // Chỉ copy các trường được phép sửa từ form sang entity đang quản lý bởi Hibernate.
        // Không thay createdBy/status tại đây để tránh người dùng tự đẩy trạng thái qua dữ liệu form.
        document.setTitle(changes.getTitle());
        document.setDescription(changes.getDescription());
        document.setSummary(changes.getSummary());
        document.setKeywords(changes.getKeywords());
        document.setLanguageCode(changes.getLanguageCode());
        document.setLearningResourceType(changes.getLearningResourceType());
        document.setEducationLevel(changes.getEducationLevel());
        // Form cũ có thể không gửi licenseType; null nghĩa là giữ nguyên giấy phép hiện tại.
        if (changes.getLicenseType() != null) document.setLicenseType(changes.getLicenseType());
        document.setAuthorName(changes.getAuthorName());
        document.setCategory(changes.getCategory());
        document.setDepartment(changes.getDepartment());
        if (changes.getFilePath() != null) {
            document.setFileName(changes.getFileName());
            document.setFilePath(changes.getFilePath());
            document.setFileType(changes.getFileType());
            document.setFileSize(changes.getFileSize());
        }
        Document saved = documentRepository.save(document);
        if (changes.getFilePath() != null) {
            recordVersion(saved, owner, normalizeNote(changeNote, "Thay tệp sau khi chỉnh sửa"));
        }
        realtimeChanged(owner.getId());
        return saved;
    }

    @Override
    public Document submit(Long documentId, User owner) {
        Document document = findOwnedDocument(documentId, owner);
        if (document.getStatus() != DocumentStatus.DRAFT
                && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new InvalidStatusException("Chỉ tài liệu nháp hoặc cần chỉnh sửa mới được gửi duyệt");
        }
        DocumentStatus oldStatus = document.getStatus();
        boolean resubmission = oldStatus == DocumentStatus.REVISION_REQUIRED;
        DocumentStatus nextStatus = resubmission ? DocumentStatus.RESUBMITTED : DocumentStatus.SUBMITTED;
        ReviewAction action = resubmission ? ReviewAction.RESUBMITTED : ReviewAction.SUBMITTED;
        document.setStatus(nextStatus);
        document.setSubmittedAt(LocalDateTime.now());
        Document saved = documentRepository.save(document);
        recordTransition(saved, owner, action, oldStatus, nextStatus,
                resubmission ? "Gửi lại tài liệu sau chỉnh sửa" : "Gửi tài liệu để kiểm duyệt");
        auditWorkflow(owner, resubmission ? AuditAction.DOCUMENT_RESUBMITTED : AuditAction.DOCUMENT_SUBMITTED,
                saved, resubmission ? "Gửi lại tài liệu sau chỉnh sửa" : "Gửi tài liệu để duyệt");
        if (notificationService != null) notificationService.submitted(saved);
        realtimeChanged(owner.getId());
        return saved;
    }

    @Override
    public void deleteDraft(Long documentId, User owner) {
        Document document = findOwnedDocument(documentId, owner);
        if (document.getStatus() != DocumentStatus.DRAFT) {
            throw new InvalidStatusException("Chỉ được xóa tài liệu đang ở trạng thái nháp");
        }
        if (versionRepository != null) {
            versionRepository.deleteByDocumentId(documentId);
        }
        documentRepository.delete(document);
        realtimeChanged(owner.getId());
    }

    private Document findOwnedDocument(Long documentId, User owner) {
        // Lấy bản ghi với khóa ghi để các thao tác sửa/gửi duyệt không ghi đè nhau khi chạy đồng thời.
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + documentId));
        if (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi tài liệu này");
        }
        return document;
    }

    private void recordVersion(Document document, User owner, String note) {
        if (versionRepository == null || document.getFilePath() == null || document.getFileName() == null) return;
        // Version number tăng theo phiên bản mới nhất của cùng tài liệu.
        // Mỗi version giữ lại filePath/checksum để có thể tải lại đúng file ở thời điểm đó.
        int nextNumber = versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(document.getId())
                .map(version -> version.getVersionNumber() + 1).orElse(1);
        versionRepository.clearCurrentVersion(document.getId());
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(nextNumber);
        version.setFileName(document.getFileName());
        version.setFilePath(document.getFilePath());
        version.setFileType(document.getFileType());
        version.setFileSize(document.getFileSize());
        version.setCreatedBy(owner);
        version.setChangeNote(note);
        version.setCurrentVersion(true);
        if (fileStorageService != null) version.setChecksum(fileStorageService.checksum(document.getFilePath()));
        DocumentVersion savedVersion = versionRepository.save(version);
        if (auditLogs != null) {
            auditLogs.recordTransactionalAsUser(owner, AuditAction.DOCUMENT_VERSION_CREATED,
                    AuditTargetType.DOCUMENT, document.getId(), document.getTitle(),
                    "Tạo phiên bản v" + savedVersion.getVersionNumber() + ": " + document.getTitle(),
                    AuditResult.SUCCESS);
        }
    }

    private void recordTransition(Document document, User actor, ReviewAction action,
                                  DocumentStatus oldStatus, DocumentStatus newStatus, String comment) {
        if (historyRepository == null) return;
        ApprovalHistory history = new ApprovalHistory();
        history.setDocument(document);
        history.setReviewer(actor);
        history.setAction(action);
        history.setComment(comment);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        historyRepository.save(history);
    }

    private void auditWorkflow(User actor, AuditAction action, Document document, String description) {
        if (auditLogs == null) return;
        auditLogs.recordTransactionalAsUser(actor, action, AuditTargetType.DOCUMENT, document.getId(),
                document.getTitle(), description + ": " + document.getTitle(), AuditResult.SUCCESS);
    }

    private String normalizeNote(String note, String fallback) {
        return note == null || note.isBlank() ? fallback : note.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void realtimeChanged(Long ownerId) {
        if (realtimeChanges != null) realtimeChanges.documentChangedAfterCommit(ownerId);
    }
}
