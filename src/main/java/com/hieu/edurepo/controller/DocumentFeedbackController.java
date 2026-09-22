package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentReportForm;
import com.hieu.edurepo.dto.DocumentReviewForm;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.DocumentReportService;
import com.hieu.edurepo.service.DocumentReviewService;
import com.hieu.edurepo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

/**
 * Bộ điều hướng tiếp nhận đánh giá câu trả lời của Trợ lý AI (Document Feedback Controller).
 * Cho phép sinh viên bấm Thích/Không thích (Upvote/Downvote) và gửi nhận xét cải thiện chất lượng bot.
 */
@Controller
public class DocumentFeedbackController {
    private final DocumentReviewService reviewService;
    private final DocumentReportService reportService;
    private final UserService userService;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public DocumentFeedbackController(DocumentReviewService reviewService, DocumentReportService reportService,
                                      UserService userService, com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.userService = userService;
        this.auditLogs = auditLogs;
    }

    @PostMapping("/document-reviews/{documentId}")
    public String createReview(@PathVariable Long documentId,
                               @Valid @ModelAttribute DocumentReviewForm documentReviewForm,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal CustomUserPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationError(documentId, bindingResult, redirectAttributes);
        try {
            reviewService.create(documentId, currentUser(principal), documentReviewForm);
            redirectAttributes.addFlashAttribute("success", "Cảm ơn bạn đã đánh giá chất lượng tài liệu.");
        } catch (InvalidStatusException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return detail(documentId);
    }

    @PostMapping("/document-reviews/{documentId}/{reviewId}")
    public String updateReview(@PathVariable Long documentId, @PathVariable Long reviewId,
                               @Valid @ModelAttribute DocumentReviewForm documentReviewForm,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal CustomUserPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationError(documentId, bindingResult, redirectAttributes);
        reviewService.update(documentId, reviewId, currentUser(principal), documentReviewForm);
        redirectAttributes.addFlashAttribute("success", "Đánh giá tài liệu của bạn đã được cập nhật.");
        return detail(documentId);
    }

    @PostMapping("/document-reviews/{documentId}/{reviewId}/delete")
    public String deleteReview(@PathVariable Long documentId, @PathVariable Long reviewId,
                               @AuthenticationPrincipal CustomUserPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        reviewService.delete(documentId, reviewId, currentUser(principal));
        redirectAttributes.addFlashAttribute("success", "Đã xóa đánh giá tài liệu của bạn.");
        return detail(documentId);
    }

    @PostMapping("/document-reports/{documentId}")
    public String report(@PathVariable Long documentId,
                         @Valid @ModelAttribute DocumentReportForm documentReportForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return validationError(documentId, bindingResult, redirectAttributes);
        try {
            var report = reportService.create(documentId, currentUser(principal), documentReportForm);
            auditLogs.record(com.hieu.edurepo.enums.AuditAction.DOCUMENT_REPORTED,
                    com.hieu.edurepo.enums.AuditTargetType.DOCUMENT, documentId,
                    "Báo cáo tài liệu với lý do: " + report.getReason().getLabel(),
                    com.hieu.edurepo.enums.AuditResult.SUCCESS);
            redirectAttributes.addFlashAttribute("success",
                    "Đã gửi báo cáo để đội ngũ kiểm duyệt xem xét. Tài liệu chưa bị xóa.");
        } catch (InvalidStatusException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return detail(documentId);
    }

    private String validationError(Long documentId, BindingResult result, RedirectAttributes attributes) {
        var fieldError = result.getFieldError();
        String message = fieldError == null || fieldError.getDefaultMessage() == null
                ? "Dữ liệu chưa hợp lệ"
                : fieldError.getDefaultMessage();
        attributes.addFlashAttribute("error", message);
        return detail(documentId);
    }

    private com.hieu.edurepo.entity.User currentUser(CustomUserPrincipal principal) {
        return userService.findById(Objects.requireNonNull(principal, "Bạn cần đăng nhập").getId());
    }

    private String detail(Long documentId) { return "redirect:/repository/" + documentId + "#document-reviews"; }
}
