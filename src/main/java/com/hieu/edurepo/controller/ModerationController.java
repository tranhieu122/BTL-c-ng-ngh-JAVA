package com.hieu.edurepo.controller;

import com.hieu.edurepo.enums.DocumentReportStatus;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.DocumentReportService;
import com.hieu.edurepo.service.DocumentReviewService;
import com.hieu.edurepo.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
@RequestMapping("/moderation")
@PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
/**
 * Bộ điều hướng kiểm duyệt báo cáo vi phạm nội dung học liệu (Moderation Controller).
 * Dành cho Quản trị viên và Ban biên tập xem xét các phản ánh vi phạm bản quyền hoặc nội dung xấu.
 */
public class ModerationController {
    private final DocumentReportService reportService;
    private final DocumentReviewService reviewService;
    private final UserService userService;

    public ModerationController(DocumentReportService reportService, DocumentReviewService reviewService,
                                UserService userService) {
        this.reportService = reportService;
        this.reviewService = reviewService;
        this.userService = userService;
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("reports", reportService.findAllForModeration());
        model.addAttribute("reviews", reviewService.findAllForModeration());
        model.addAttribute("reportStatuses", DocumentReportStatus.values());
        model.addAttribute("seriousDocumentIds", reportService.seriouslyReportedDocumentIds());
        return "moderation/reports";
    }

    @PostMapping("/reports/{reportId}/status")
    public String updateReport(@PathVariable Long reportId, @RequestParam DocumentReportStatus status,
                               @AuthenticationPrincipal CustomUserPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        reportService.updateStatus(reportId, status,
                userService.findById(Objects.requireNonNull(principal, "Bạn cần đăng nhập").getId()));
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật trạng thái báo cáo.");
        return "redirect:/moderation/reports";
    }

    @PostMapping("/reviews/{reviewId}/visibility")
    public String reviewVisibility(@PathVariable Long reviewId, @RequestParam boolean hidden,
                                   RedirectAttributes redirectAttributes) {
        reviewService.setHidden(reviewId, hidden);
        redirectAttributes.addFlashAttribute("success", hidden
                ? "Đã ẩn nhận xét khỏi trang tài liệu." : "Đã hiển thị lại nhận xét.");
        return "redirect:/moderation/reports#review-moderation";
    }
}
