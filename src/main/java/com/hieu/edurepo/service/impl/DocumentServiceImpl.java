package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentVersion;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.LicenseType;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentVersionRepository;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.NotificationService;
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

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public DocumentServiceImpl(DocumentRepository documentRepository) {
        this(documentRepository, null, null, null);
    }

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentVersionRepository versionRepository,
                               FileStorageService fileStorageService) {
        this(documentRepository, versionRepository, fileStorageService, null);
    }

    @Autowired
    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentVersionRepository versionRepository,
                               FileStorageService fileStorageService,
                               NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
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
        return documentRepository.findByStatusInOrderBySubmittedAtAsc(
                List.of(DocumentStatus.SUBMITTED, DocumentStatus.APPROVED));
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
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.DRAFT);
        Document saved = documentRepository.save(document);
        recordVersion(saved, owner, "Phiên bản ban đầu");
        return saved;
    }

    @Override
    public Document submitNew(Document document, User owner) {
        document.setCreatedBy(owner);
        if (canPublishDirectly(owner)) {
            document.setStatus(DocumentStatus.PUBLISHED);
            document.setPublishedAt(LocalDateTime.now());
        } else {
            document.setStatus(DocumentStatus.SUBMITTED);
            document.setSubmittedAt(LocalDateTime.now());
        }
        Document saved = documentRepository.save(document);
        recordVersion(saved, owner, "Phiên bản ban đầu");
        if (notificationService != null && saved.getStatus() == DocumentStatus.SUBMITTED) {
            notificationService.submitted(saved);
        }
        return saved;
    }

    @Override
    public Document updateDraft(Long documentId, Document changes, User owner) {
        Document document = findOwnedDocument(documentId, owner);
        if (document.getStatus() != DocumentStatus.DRAFT
                && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new InvalidStatusException("Chỉ tài liệu nháp hoặc cần chỉnh sửa mới được cập nhật");
        }

        document.setTitle(changes.getTitle());
        document.setDescription(changes.getDescription());
        document.setSummary(changes.getSummary());
        document.setKeywords(changes.getKeywords());
        document.setLanguageCode(changes.getLanguageCode());
        document.setLearningResourceType(changes.getLearningResourceType());
        document.setEducationLevel(changes.getEducationLevel());
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
            recordVersion(saved, owner, "Thay tệp sau khi chỉnh sửa");
        }
        return saved;
    }

    @Override
    public Document submit(Long documentId, User owner) {
        Document document = findOwnedDocument(documentId, owner);
        if (document.getStatus() != DocumentStatus.DRAFT
                && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new InvalidStatusException("Chỉ tài liệu nháp hoặc cần chỉnh sửa mới được gửi duyệt");
        }
        if (canPublishDirectly(owner)) {
            document.setStatus(DocumentStatus.PUBLISHED);
            document.setPublishedAt(LocalDateTime.now());
        } else {
            document.setStatus(DocumentStatus.SUBMITTED);
            document.setSubmittedAt(LocalDateTime.now());
        }
        Document saved = documentRepository.save(document);
        if (notificationService != null && saved.getStatus() == DocumentStatus.SUBMITTED) {
            notificationService.submitted(saved);
        }
        return saved;
    }

    @Override
    public Document changeStatus(Long documentId, DocumentStatus status) {
        Document document = findById(documentId);
        document.setStatus(status);
        if (status == DocumentStatus.PUBLISHED) {
            document.setPublishedAt(LocalDateTime.now());
        }
        return documentRepository.save(document);
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
    }

    private Document findOwnedDocument(Long documentId, User owner) {
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + documentId));
        if (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi tài liệu này");
        }
        return document;
    }

    private boolean canPublishDirectly(User owner) {
        return owner.getRoles().stream()
                .map(role -> role.getName())
                .anyMatch(role -> role == RoleName.ADMIN || role == RoleName.REVIEWER);
    }

    private void recordVersion(Document document, User owner, String note) {
        if (versionRepository == null || document.getFilePath() == null || document.getFileName() == null) return;
        int nextNumber = versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(document.getId())
                .map(version -> version.getVersionNumber() + 1).orElse(1);
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(nextNumber);
        version.setFileName(document.getFileName());
        version.setFilePath(document.getFilePath());
        version.setFileType(document.getFileType());
        version.setFileSize(document.getFileSize());
        version.setCreatedBy(owner);
        version.setChangeNote(note);
        if (fileStorageService != null) version.setChecksum(fileStorageService.checksum(document.getFilePath()));
        versionRepository.save(version);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
