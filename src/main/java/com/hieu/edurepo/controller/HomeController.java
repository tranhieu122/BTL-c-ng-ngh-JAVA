package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.CategoryService;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.LibraryService;
import com.hieu.edurepo.service.DocumentReviewService;
import com.hieu.edurepo.dto.DocumentReviewForm;
import com.hieu.edurepo.dto.DocumentReportForm;
import com.hieu.edurepo.enums.DocumentReportReason;
import com.hieu.edurepo.security.CustomUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final DocumentService documentService;
    private final CategoryService categoryService;
    private final DocumentRepository documentRepository;
    private final LibraryService libraryService;
    private final DocumentReviewService documentReviewService;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public HomeController(DocumentService documentService, CategoryService categoryService,
                          DocumentRepository documentRepository, LibraryService libraryService,
                          DocumentReviewService documentReviewService,
                          com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.documentService = documentService;
        this.categoryService = categoryService;
        this.documentRepository = documentRepository;
        this.libraryService = libraryService;
        this.documentReviewService = documentReviewService;
        this.auditLogs = auditLogs;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Category> activeCategories = categoryService.findActive();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        documentRepository.countPublishedByCategory().forEach(item -> categoryCounts.put(item.name(), item.count()));
        model.addAttribute("featuredDocuments", documentService.searchPublished("",
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "publishedAt"))).getContent());
        model.addAttribute("exploreCategories", activeCategories.stream().limit(6).toList());
        model.addAttribute("categoryCounts", categoryCounts);
        model.addAttribute("publishedDocumentCount", documentRepository.countByStatus(DocumentStatus.PUBLISHED));
        model.addAttribute("activeCategoryCount", activeCategories.size());
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.HOME_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.PAGE, null, "Trang chủ",
                "Xem trang chủ EduRepo", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "public/home";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN")) return "redirect:/admin/dashboard";
        if (hasRole(authentication, "ROLE_REVIEWER")) return "redirect:/reviews/pending";
        if (hasRole(authentication, "ROLE_SUBMITTER")) return "redirect:/documents";
        return "redirect:/";
    }

    @GetMapping("/repository")
    public String repository(@RequestParam(defaultValue = "") String keyword,
                             @RequestParam(required = false) Long categoryId,
                             @RequestParam(required = false) LearningResourceType resourceType,
                             @RequestParam(required = false) EducationLevel educationLevel,
                             @RequestParam(defaultValue = "") String languageCode,
                             @RequestParam(defaultValue = "0") int page,
                             Model model) {
        int safePage = Math.max(page, 0);
        Page<Document> documents = documentService.searchPublished(keyword, categoryId, resourceType,
                educationLevel, null, languageCode,
                PageRequest.of(safePage, 12, Sort.by(Sort.Direction.DESC, "publishedAt")));
        model.addAttribute("documents", documents);
        model.addAttribute("categories", categoryService.findActive());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedResourceType", resourceType);
        model.addAttribute("selectedEducationLevel", educationLevel);
        model.addAttribute("selectedLanguageCode", languageCode);
        model.addAttribute("resourceTypes", LearningResourceType.values());
        model.addAttribute("educationLevels", EducationLevel.values());
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.REPOSITORY_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.PAGE, null, "Kho tài liệu",
                keyword == null || keyword.isBlank() ? "Xem kho tài liệu"
                        : "Tìm trong kho tài liệu với từ khóa: " + keyword.trim(),
                com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "public/repository";
    }

    @GetMapping("/repository/{id}")
    public String publicDetail(@PathVariable Long id,
                               @AuthenticationPrincipal CustomUserPrincipal principal,
                               Model model) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Tài liệu chưa được công bố");
        }
        documentService.recordView(id);
        document = documentService.findById(id);
        Long userId = principal == null ? null : principal.getId();
        model.addAttribute("document", document);
        model.addAttribute("bookmarked", libraryService.isBookmarked(userId, id));
        model.addAttribute("userCollections", userId == null ? List.of() : libraryService.collections(userId));
        model.addAttribute("recommendations", libraryService.recommend(userId, document, 4));
        var ownReview = documentReviewService.findOwn(id, userId).orElse(null);
        DocumentReviewForm reviewForm = new DocumentReviewForm();
        if (ownReview != null) {
            reviewForm.setRating(ownReview.getRating());
            reviewForm.setComment(ownReview.getComment());
            reviewForm.setHelpful(ownReview.isHelpful());
            reviewForm.setEasyToUnderstand(ownReview.isEasyToUnderstand());
            reviewForm.setOnTopic(ownReview.isOnTopic());
            reviewForm.setGoodFileQuality(ownReview.isGoodFileQuality());
        }
        model.addAttribute("reviewSummary", documentReviewService.summary(id));
        model.addAttribute("documentReviews", documentReviewService.visibleComments(id));
        model.addAttribute("ownReview", ownReview);
        model.addAttribute("documentReviewForm", reviewForm);
        model.addAttribute("documentReportForm", new DocumentReportForm());
        model.addAttribute("reportReasons", DocumentReportReason.values());
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.DOCUMENT_DETAIL_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.DOCUMENT, document.getId(), document.getTitle(),
                "Xem chi tiết tài liệu: " + document.getTitle(),
                com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "public/document-detail";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
