package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.CollectionForm;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.LibraryService;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bộ điều hướng Thư viện học tập cá nhân của sinh viên (Library Controller).
 * Quản lý danh sách tài liệu yêu thích (Bookmark) và các bộ sưu tập tài liệu theo chuyên đề.
 */
@Controller
@RequestMapping("/library")
public class LibraryController {
    private final LibraryService libraryService;
    private final UserService userService;
    private final com.hieu.edurepo.service.DocumentService documentService;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;
    private final com.hieu.edurepo.service.UserActivityService userActivityService;

    public LibraryController(LibraryService libraryService, UserService userService,
                             com.hieu.edurepo.service.DocumentService documentService,
                             com.hieu.edurepo.service.AuditLogService auditLogs,
                             com.hieu.edurepo.service.UserActivityService userActivityService) {
        this.libraryService = libraryService;
        this.userService = userService;
        this.documentService = documentService;
        this.auditLogs = auditLogs;
        this.userActivityService = userActivityService;
    }

    @GetMapping("/recently-viewed")
    public String recentlyViewed(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        model.addAttribute("items", userActivityService.recentlyViewed(requirePrincipal(principal).getId()));
        model.addAttribute("activityType", "views");
        return "library/activity";
    }

    @GetMapping("/download-history")
    public String downloadHistory(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        model.addAttribute("items", userActivityService.downloadHistory(requirePrincipal(principal).getId()));
        model.addAttribute("activityType", "downloads");
        return "library/activity";
    }

    @GetMapping("/my-reviews")
    public String myReviews(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        model.addAttribute("reviews", userActivityService.reviews(requirePrincipal(principal).getId()));
        return "library/my-reviews";
    }

    @GetMapping
    public String library(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        Long userId = requirePrincipal(principal).getId();
        addLibraryModel(model, userId);
        if (!model.containsAttribute("collectionForm")) model.addAttribute("collectionForm", new CollectionForm());
        return "library/index";
    }

    @PostMapping("/bookmarks/{documentId}")
    public String toggleBookmark(@PathVariable Long documentId,
                                 @AuthenticationPrincipal CustomUserPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        var user = userService.findById(requirePrincipal(principal).getId());
        boolean saved = libraryService.toggleBookmark(user, documentId);
        var document = documentService.findById(documentId);
        auditLogs.record(saved ? com.hieu.edurepo.enums.AuditAction.BOOKMARK_SAVED
                        : com.hieu.edurepo.enums.AuditAction.BOOKMARK_REMOVED,
                com.hieu.edurepo.enums.AuditTargetType.BOOKMARK, documentId, document.getTitle(),
                (saved ? "Lưu bookmark tài liệu: " : "Bỏ bookmark tài liệu: ") + document.getTitle(),
                com.hieu.edurepo.enums.AuditResult.SUCCESS);
        redirectAttributes.addFlashAttribute("success", saved
                ? "Đã lưu tài liệu vào thư viện" : "Đã bỏ tài liệu khỏi thư viện");
        return "redirect:/repository/" + documentId;
    }

    @PostMapping("/collections")
    public String createCollection(@Valid @ModelAttribute("collectionForm") CollectionForm form,
                                   BindingResult bindingResult,
                                   @AuthenticationPrincipal CustomUserPrincipal principal,
                                   Model model, RedirectAttributes redirectAttributes) {
        Long userId = requirePrincipal(principal).getId();
        if (bindingResult.hasErrors()) {
            addLibraryModel(model, userId);
            return "library/index";
        }
        try { libraryService.createCollection(userService.findById(userId), form.getName(), form.getDescription()); }
        catch (com.hieu.edurepo.exception.InvalidStatusException exception) {
            bindingResult.rejectValue("name", "invalid", exception.getMessage());
            addLibraryModel(model, userId);
            return "library/index";
        }
        redirectAttributes.addFlashAttribute("success", "Đã tạo bộ sưu tập");
        return "redirect:/library";
    }

    @GetMapping("/collections/{collectionId}")
    public String collection(@PathVariable Long collectionId,
                             @AuthenticationPrincipal CustomUserPrincipal principal,
                             Model model) {
        Long userId = requirePrincipal(principal).getId();
        var collection = libraryService.findOwnedCollection(collectionId, userId);
        model.addAttribute("collection", collection);
        if (!model.containsAttribute("collectionForm")) {
            CollectionForm form = new CollectionForm();
            form.setName(collection.getName());
            form.setDescription(collection.getDescription());
            model.addAttribute("collectionForm", form);
        }
        model.addAttribute("items", libraryService.collectionItems(collectionId, userId));
        return "library/detail";
    }

    @PostMapping("/collections/{collectionId}")
    public String updateCollection(@PathVariable Long collectionId,
                                   @Valid @ModelAttribute("collectionForm") CollectionForm form,
                                   BindingResult result, @AuthenticationPrincipal CustomUserPrincipal principal,
                                   Model model, RedirectAttributes redirectAttributes) {
        Long ownerId = requirePrincipal(principal).getId();
        libraryService.findOwnedCollection(collectionId, ownerId);
        if (result.hasErrors()) return collection(collectionId, principal, model);
        try { libraryService.updateCollection(collectionId, ownerId, form.getName(), form.getDescription()); }
        catch (com.hieu.edurepo.exception.InvalidStatusException exception) {
            result.rejectValue("name", "invalid", exception.getMessage());
            return collection(collectionId, principal, model);
        }
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật bộ sưu tập");
        return "redirect:/library/collections/" + collectionId;
    }

    @PostMapping("/collections/{collectionId}/items/{documentId}")
    public String addToCollection(@PathVariable Long collectionId, @PathVariable Long documentId,
                                  @AuthenticationPrincipal CustomUserPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        libraryService.addToCollection(collectionId, documentId, requirePrincipal(principal).getId());
        redirectAttributes.addFlashAttribute("success", "Đã thêm tài liệu vào bộ sưu tập");
        return "redirect:/repository/" + documentId;
    }

    @PostMapping("/collections/{collectionId}/items/{documentId}/remove")
    public String removeFromCollection(@PathVariable Long collectionId, @PathVariable Long documentId,
                                       @AuthenticationPrincipal CustomUserPrincipal principal,
                                       RedirectAttributes redirectAttributes) {
        libraryService.removeFromCollection(collectionId, documentId, requirePrincipal(principal).getId());
        redirectAttributes.addFlashAttribute("success", "Đã xóa tài liệu khỏi bộ sưu tập");
        return "redirect:/library/collections/" + collectionId;
    }

    @PostMapping("/collections/{collectionId}/delete")
    public String deleteCollection(@PathVariable Long collectionId,
                                   @AuthenticationPrincipal CustomUserPrincipal principal,
                                   RedirectAttributes redirectAttributes) {
        libraryService.deleteCollection(collectionId, requirePrincipal(principal).getId());
        redirectAttributes.addFlashAttribute("success", "Đã xóa bộ sưu tập");
        return "redirect:/library";
    }

    private void addLibraryModel(Model model, Long userId) {
        var collections = libraryService.collections(userId);
        Map<Long, Long> collectionSizes = new LinkedHashMap<>();
        collections.forEach(collection -> collectionSizes.put(collection.getId(),
                libraryService.collectionSize(collection.getId())));
        model.addAttribute("bookmarks", libraryService.bookmarks(userId));
        model.addAttribute("collections", collections);
        model.addAttribute("collectionSizes", collectionSizes);
    }

    private CustomUserPrincipal requirePrincipal(CustomUserPrincipal principal) {
        return Objects.requireNonNull(principal, "Bạn cần đăng nhập để sử dụng thư viện cá nhân");
    }
}
