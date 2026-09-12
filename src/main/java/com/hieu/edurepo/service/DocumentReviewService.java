package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentReviewForm;
import com.hieu.edurepo.dto.DocumentReviewSummary;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.User;

import java.util.List;
import java.util.Optional;

public interface DocumentReviewService {
    DocumentReview create(Long documentId, User user, DocumentReviewForm form);
    DocumentReview update(Long documentId, Long reviewId, User user, DocumentReviewForm form);
    void delete(Long documentId, Long reviewId, User user);
    Optional<DocumentReview> findOwn(Long documentId, Long userId);
    List<DocumentReview> visibleComments(Long documentId);
    DocumentReviewSummary summary(Long documentId);
    List<DocumentReview> findAllForModeration();
    void setHidden(Long reviewId, boolean hidden);
}
