package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentVersion;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.LicenseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DocumentService {
    Document findById(Long id);
    List<Document> findByOwner(Long userId);
    Page<Document> searchByOwner(Long userId, String keyword, DocumentStatus status, Long categoryId,
                                 Long facultyId, Long departmentId, Pageable pageable);
    List<Document> findPendingReview();
    Page<Document> searchPendingReview(String keyword, DocumentStatus status, Pageable pageable);
    long countPendingReview(DocumentStatus status);
    Page<Document> searchPublished(String keyword, Pageable pageable);
    Page<Document> searchPublished(String keyword, Long categoryId, LearningResourceType resourceType,
                                   EducationLevel educationLevel, LicenseType licenseType,
                                   String languageCode, Pageable pageable);
    List<Document> findRelatedPublished(Document document, int limit);
    List<DocumentVersion> findVersions(Long documentId);
    Page<DocumentVersion> findVersions(Long documentId, Pageable pageable);
    DocumentVersion findVersion(Long documentId, Long versionId);
    List<String> versionFilePaths(Long documentId);
    void recordView(Long documentId);
    void recordDownload(Long documentId);
    Document saveDraft(Document document, User owner);
    Document submitNew(Document document, User owner);
    Document updateDraft(Long documentId, Document changes, User owner);
    Document updateDraft(Long documentId, Document changes, User owner, String changeNote);
    Document submit(Long documentId, User owner);
    void deleteDraft(Long documentId, User owner);
}
