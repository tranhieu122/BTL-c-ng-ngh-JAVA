package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.dto.DocumentReviewForm;
import com.hieu.edurepo.dto.DocumentReviewSummary;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.service.DocumentReviewService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
/**
 * Triển khai dịch vụ đánh giá, thẩm định và bình luận tài liệu (Document Review Service Implementation).
 * <p>
 * Quản lý các nhận xét công khai của sinh viên, tính điểm sao trung bình của học liệu,
 * và hỗ trợ Quản trị viên ẩn các nhận xét vi phạm tiêu chuẩn cộng đồng.
 * </p>
 */
public class DocumentReviewServiceImpl implements DocumentReviewService {
    private final DocumentReviewRepository reviewRepository;
    private final DocumentRepository documentRepository;

    public DocumentReviewServiceImpl(DocumentReviewRepository reviewRepository, DocumentRepository documentRepository) {
        this.reviewRepository = reviewRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public DocumentReview create(Long documentId, User user, DocumentReviewForm form) {
        Document document = requirePublished(documentId);
        validate(form);
        if (reviewRepository.existsByDocumentIdAndUserId(documentId, user.getId())) {
            throw new InvalidStatusException("Bạn đã đánh giá tài liệu này. Hãy chỉnh sửa đánh giá hiện có.");
        }
        DocumentReview review = new DocumentReview();
        review.setDocument(document);
        review.setUser(user);
        apply(review, form);
        try {
            return reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidStatusException("Bạn đã đánh giá tài liệu này. Hãy chỉnh sửa đánh giá hiện có.");
        }
    }

    @Override
    public DocumentReview update(Long documentId, Long reviewId, User user, DocumentReviewForm form) {
        requirePublished(documentId);
        validate(form);
        DocumentReview review = ownedReview(documentId, reviewId, user.getId());
        apply(review, form);
        return reviewRepository.save(review);
    }

    @Override
    public void delete(Long documentId, Long reviewId, User user) {
        DocumentReview review = ownedReview(documentId, reviewId, user.getId());
        reviewRepository.delete(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentReview> findOwn(Long documentId, Long userId) {
        return userId == null ? Optional.empty() : reviewRepository.findByDocumentIdAndUserId(documentId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentReview> visibleComments(Long documentId) {
        return reviewRepository.findByDocumentIdAndHiddenFalseAndCommentIsNotNullOrderByCreatedAtDesc(documentId)
                .stream().filter(review -> !review.getComment().isBlank()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentReviewSummary summary(Long documentId) {
        return new DocumentReviewSummary(reviewRepository.averageVisibleRating(documentId),
                reviewRepository.countByDocumentIdAndHiddenFalse(documentId));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @Transactional(readOnly = true)
    public List<DocumentReview> findAllForModeration() {
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public void setHidden(Long reviewId, boolean hidden) {
        DocumentReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá"));
        review.setHidden(hidden);
        reviewRepository.save(review);
    }

    private Document requirePublished(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu"));
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new InvalidStatusException("Chỉ tài liệu đã công bố mới có thể được đánh giá.");
        }
        return document;
    }

    private DocumentReview ownedReview(Long documentId, Long reviewId, Long userId) {
        DocumentReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá"));
        if (!review.getDocument().getId().equals(documentId) || !review.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Bạn chỉ có thể thay đổi đánh giá của chính mình.");
        }
        return review;
    }

    private void validate(DocumentReviewForm form) {
        if (form == null || form.getRating() == null || form.getRating() < 1 || form.getRating() > 5) {
            throw new IllegalArgumentException("Số sao phải từ 1 đến 5");
        }
        if (form.getComment() != null && form.getComment().length() > 500) {
            throw new IllegalArgumentException("Nhận xét không được vượt quá 500 ký tự");
        }
    }

    private void apply(DocumentReview review, DocumentReviewForm form) {
        review.setRating(form.getRating());
        review.setComment(normalize(form.getComment()));
        review.setHelpful(form.isHelpful());
        review.setEasyToUnderstand(form.isEasyToUnderstand());
        review.setOnTopic(form.isOnTopic());
        review.setGoodFileQuality(form.isGoodFileQuality());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
