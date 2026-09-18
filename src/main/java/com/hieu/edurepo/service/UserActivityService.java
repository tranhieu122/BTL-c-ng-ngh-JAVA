package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.DocumentDownloadHistory;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.DocumentViewHistory;

import java.util.List;

public interface UserActivityService {
    void recordView(Long userId, Long documentId);
    void recordDownload(Long userId, Long documentId);
    List<DocumentViewHistory> recentlyViewed(Long userId);
    List<DocumentDownloadHistory> downloadHistory(Long userId);
    List<DocumentReview> reviews(Long userId);
}
