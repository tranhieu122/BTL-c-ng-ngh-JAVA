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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    private static final Set<DocumentStatus> QUEUE_STATUSES = EnumSet.of(
            DocumentStatus.SUBMITTED,
            DocumentStatus.RESUBMITTED,
            DocumentStatus.UNDER_REVIEW,
            DocumentStatus.APPROVED);

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
    public String pending(@RequestParam(defaultValue = "") String keyword,
                          @RequestParam(required = false) DocumentStatus status,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        DocumentStatus selectedStatus = QUEUE_STATUSES.contains(status) ? status : null;
        var documentPage = documentService.searchPendingReview(keyword, selectedStatus,
                PageRequest.of(Math.max(0, page), 10));
        long submittedCount = documentService.countPendingReview(DocumentStatus.SUBMITTED)
                + documentService.countPendingReview(DocumentStatus.RESUBMITTED);
        long underReviewCount = documentService.countPendingReview(DocumentStatus.UNDER_REVIEW);
        long approvedCount = documentService.countPendingReview(DocumentStatus.APPROVED);
        model.addAttribute("documents", documentPage.getContent());
        model.addAttribute("documentPage", documentPage);
        model.addAttribute("queueTotal", submittedCount + underReviewCount + approvedCount);
        model.addAttribute("submittedCount", submittedCount);
        model.addAttribute("underReviewCount", underReviewCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("selectedStatus", selectedStatus);
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.REVIEW_QUEUE_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.PAGE, null, "Hàng chờ duyệt",
                "Xem hàng chờ duyệt tài liệu", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "reviews/pending";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(defaultValue = "0") int historyPage,
                         @RequestParam(defaultValue = "0") int versionPage,
                         Model model) {
        populateReviewModel(id, historyPage, versionPage, model);
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
        if (bindingResult.hasErrors()) {
            populateReviewModel(id, 0, 0, model);
            return "reviews/detail";
        }
        reviewService.review(id, form.getAction(), form.getComment(),
                userService.findById(requirePrincipal(principal).getId()), form.getContentQualityScore(),
                form.getTeachingEffectivenessScore(), form.getEaseOfUseScore());
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật kết quả duyệt");
        if (form.getAction() == com.hieu.edurepo.enums.ReviewAction.PUBLISHED) {
            return "redirect:/repository/" + id;
        }
        return "redirect:/reviews/pending";
    }

    private CustomUserPrincipal requirePrincipal(CustomUserPrincipal principal) {
        return Objects.requireNonNull(principal, "Bạn cần đăng nhập để thực hiện thao tác này");
    }

    private Document findReviewableDocument(Long id) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.SUBMITTED
                && document.getStatus() != DocumentStatus.RESUBMITTED
                && document.getStatus() != DocumentStatus.UNDER_REVIEW
                && document.getStatus() != DocumentStatus.APPROVED) {
            throw new ResourceNotFoundException("Tài liệu không nằm trong hàng chờ kiểm duyệt");
        }
        return document;
    }

    private void populateReviewModel(Long id, int historyPage, int versionPage, Model model) {
        model.addAttribute("document", findReviewableDocument(id));
        var history = reviewService.history(id, PageRequest.of(Math.max(0, historyPage), 10));
        var versions = documentService.findVersions(id, PageRequest.of(Math.max(0, versionPage), 10));
        model.addAttribute("history", history.getContent());
        model.addAttribute("historyPage", history);
        model.addAttribute("versions", versions.getContent());
        model.addAttribute("versionPage", versions);
    }

}
