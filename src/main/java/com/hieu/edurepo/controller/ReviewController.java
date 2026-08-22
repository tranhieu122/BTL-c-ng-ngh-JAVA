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

    public ReviewController(DocumentService documentService, ReviewService reviewService,
                            UserService userService) {
        this.documentService = documentService;
        this.reviewService = reviewService;
        this.userService = userService;
    }

    @GetMapping("/pending")
    public String pending(Model model) {
        model.addAttribute("documents", documentService.findPendingReview());
        return "reviews/pending";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("document", findReviewableDocument(id));
        model.addAttribute("history", reviewService.history(id));
        model.addAttribute("reviewForm", new ReviewForm());
        return "reviews/detail";
    }

    @PostMapping("/{id}")
    public String review(@PathVariable Long id, @Valid @ModelAttribute("reviewForm") ReviewForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserPrincipal principal,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("document", findReviewableDocument(id));
            model.addAttribute("history", reviewService.history(id));
            return "reviews/detail";
        }
        reviewService.review(id, form.getAction(), form.getComment(),
                userService.findById(requirePrincipal(principal).getId()));
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
}
