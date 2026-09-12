package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.ReviewForm;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.ReviewService;
import com.hieu.edurepo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    private final DocumentService documentService;
    private final ReviewService reviewService;
    private final UserService userService;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public ReviewController(DocumentService documentService, ReviewService reviewService,
                            UserService userService, com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.documentService = documentService;
        this.reviewService = reviewService;
        this.userService = userService;
        this.auditLogs = auditLogs;
    }

    @GetMapping("/pending")
    public String pending(Model model) {
        var documents = documentService.findPendingReview();
        model.addAttribute("documents", documents);
        model.addAttribute("submittedCount", documents.stream()
                .filter(document -> document.getStatus() == DocumentStatus.SUBMITTED).count());
        model.addAttribute("approvedCount", documents.stream()
                .filter(document -> document.getStatus() == DocumentStatus.APPROVED).count());
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.REVIEW_QUEUE_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.PAGE, null, "Hàng chờ duyệt",
                "Xem hàng chờ duyệt tài liệu", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "reviews/pending";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        populateReviewModel(id, model);
        Document document = (Document) model.getAttribute("document");
        model.addAttribute("reviewForm", new ReviewForm());
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.REVIEW_DOCUMENT_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.REVIEW, id, document == null ? null : document.getTitle(),
                "Xem chi tiết tài liệu cần duyệt" + (document == null ? "" : ": " + document.getTitle()),
                com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "reviews/detail";
    }

    @PostMapping("/{id}")
    public String review(@PathVariable Long id, @Valid @ModelAttribute("reviewForm") ReviewForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserPrincipal principal,
                         Model model, RedirectAttributes redirectAttributes) {
        Document document = findReviewableDocument(id);
        if (document.getStatus() == DocumentStatus.SUBMITTED) {
            requireRubricScore(form.getContentQualityScore(), "contentQualityScore", bindingResult);
            requireRubricScore(form.getTeachingEffectivenessScore(), "teachingEffectivenessScore", bindingResult);
            requireRubricScore(form.getEaseOfUseScore(), "easeOfUseScore", bindingResult);
        }
        if (bindingResult.hasErrors()) {
            populateReviewModel(id, model);
            return "reviews/detail";
        }
        reviewService.review(id, form.getAction(), form.getComment(),
                userService.findById(requirePrincipal(principal).getId()), form.getContentQualityScore(),
                form.getTeachingEffectivenessScore(), form.getEaseOfUseScore());
        auditLogs.record(auditAction(form.getAction()), com.hieu.edurepo.enums.AuditTargetType.REVIEW, id,
                document.getTitle(),
                auditDescription(form.getAction(), document), com.hieu.edurepo.enums.AuditResult.SUCCESS);
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật kết quả duyệt");
        return "redirect:/reviews/pending";
    }

    private CustomUserPrincipal requirePrincipal(CustomUserPrincipal principal) {
        return Objects.requireNonNull(principal, "Bạn cần đăng nhập để thực hiện thao tác này");
    }

    private Document findReviewableDocument(Long id) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.SUBMITTED
                && document.getStatus() != DocumentStatus.APPROVED) {
            throw new ResourceNotFoundException("Tài liệu không nằm trong hàng chờ kiểm duyệt");
        }
        return document;
    }

    private void populateReviewModel(Long id, Model model) {
        model.addAttribute("document", findReviewableDocument(id));
        model.addAttribute("history", reviewService.history(id));
        model.addAttribute("versions", documentService.findVersions(id));
    }

    private void requireRubricScore(Integer score, String field, BindingResult bindingResult) {
        if (score == null) {
            bindingResult.rejectValue(field, "required", "Vui lòng chấm tiêu chí này");
        }
    }

    private com.hieu.edurepo.enums.AuditAction auditAction(com.hieu.edurepo.enums.ReviewAction action) {
        return switch (action) {
            case APPROVED -> com.hieu.edurepo.enums.AuditAction.DOCUMENT_APPROVED;
            case REJECTED -> com.hieu.edurepo.enums.AuditAction.DOCUMENT_REJECTED;
            case REVISION_REQUESTED -> com.hieu.edurepo.enums.AuditAction.DOCUMENT_REVISION_REQUESTED;
            case PUBLISHED -> com.hieu.edurepo.enums.AuditAction.DOCUMENT_PUBLISHED;
            case SUBMITTED -> com.hieu.edurepo.enums.AuditAction.DOCUMENT_SUBMITTED;
        };
    }

    private String auditDescription(com.hieu.edurepo.enums.ReviewAction action, Document document) {
        return auditAction(action).getLabel() + ": " + document.getTitle();
    }
}
