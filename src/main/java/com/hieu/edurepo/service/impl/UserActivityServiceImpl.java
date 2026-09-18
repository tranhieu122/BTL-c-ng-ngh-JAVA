package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.DocumentDownloadHistory;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.DocumentViewHistory;
import com.hieu.edurepo.repository.DocumentDownloadHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.repository.DocumentViewHistoryRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.UserActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UserActivityServiceImpl implements UserActivityService {
    private final DocumentViewHistoryRepository views;
    private final DocumentDownloadHistoryRepository downloads;
    private final DocumentReviewRepository reviews;
    private final UserRepository users;
    private final DocumentRepository documents;

    public UserActivityServiceImpl(DocumentViewHistoryRepository views,
                                   DocumentDownloadHistoryRepository downloads,
                                   DocumentReviewRepository reviews,
                                   UserRepository users,
                                   DocumentRepository documents) {
        this.views = views;
        this.downloads = downloads;
        this.reviews = reviews;
        this.users = users;
        this.documents = documents;
    }

    @Override
    public void recordView(Long userId, Long documentId) {
        if (userId == null) return;
        var user = users.lockById(userId)
                .orElseThrow(() -> new com.hieu.edurepo.exception.ResourceNotFoundException("Không tìm thấy người dùng"));
        DocumentViewHistory history = views.findByUserIdAndDocumentId(userId, documentId)
                .orElseGet(() -> {
                    DocumentViewHistory item = new DocumentViewHistory();
                    item.setUser(user);
                    item.setDocument(documents.getReferenceById(documentId));
                    return item;
                });
        history.setViewedAt(LocalDateTime.now());
        views.save(history);
    }

    @Override
    public void recordDownload(Long userId, Long documentId) {
        if (userId == null) return;
        var user = users.lockById(userId)
                .orElseThrow(() -> new com.hieu.edurepo.exception.ResourceNotFoundException("Không tìm thấy người dùng"));
        DocumentDownloadHistory history = downloads.findByUserIdAndDocumentId(userId, documentId)
                .orElseGet(() -> {
                    DocumentDownloadHistory item = new DocumentDownloadHistory();
                    item.setUser(user);
                    item.setDocument(documents.getReferenceById(documentId));
                    item.setDownloadCount(0);
                    return item;
                });
        history.setDownloadedAt(LocalDateTime.now());
        history.setDownloadCount(history.getDownloadCount() + 1);
        downloads.save(history);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentViewHistory> recentlyViewed(Long userId) {
        return views.findByUserIdOrderByViewedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDownloadHistory> downloadHistory(Long userId) {
        return downloads.findByUserIdOrderByDownloadedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentReview> reviews(Long userId) {
        return reviews.findByUserIdOrderByUpdatedAtDesc(userId);
    }
}
