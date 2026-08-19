package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.service.CategoryService;
import com.hieu.edurepo.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final DocumentService documentService;
    private final CategoryService categoryService;

    public HomeController(DocumentService documentService, CategoryService categoryService) {
        this.documentService = documentService;
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/repository";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN")) return "redirect:/admin/dashboard";
        if (hasRole(authentication, "ROLE_REVIEWER")) return "redirect:/reviews/pending";
        if (hasRole(authentication, "ROLE_SUBMITTER")) return "redirect:/documents";
        return "redirect:/repository";
    }

    @GetMapping("/repository")
    public String repository(@RequestParam(defaultValue = "") String keyword,
                             @RequestParam(defaultValue = "0") int page,
                             Model model) {
        Page<Document> documents = documentService.searchPublished(
                keyword,
                PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "publishedAt")));
        model.addAttribute("documents", documents);
        model.addAttribute("categories", categoryService.findActive());
        model.addAttribute("keyword", keyword);
        return "public/repository";
    }

    @GetMapping("/repository/{id}")
    public String publicDetail(@PathVariable Long id, Model model) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Tài liệu chưa được công bố");
        }
        model.addAttribute("document", document);
        return "public/document-detail";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
