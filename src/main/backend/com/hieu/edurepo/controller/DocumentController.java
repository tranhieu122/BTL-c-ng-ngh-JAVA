package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentForm;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.DepartmentRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.CategoryService;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final DepartmentRepository departmentRepository;
    private final FileStorageService fileStorageService;

    public DocumentController(DocumentService documentService, UserService userService,
            CategoryService categoryService, DepartmentRepository departmentRepository,
            FileStorageService fileStorageService) {
        this.documentService = documentService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.departmentRepository = departmentRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public String myDocuments(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        model.addAttribute("documents", documentService.findByOwner(requirePrincipal(principal).getId()));
        return "documents/my-documents";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("documentForm", new DocumentForm());
        addReferenceData(model);
        return "documents/form";
    }

    @SuppressWarnings("null")
    @PostMapping
    public String create(@Valid @ModelAttribute("documentForm") DocumentForm form, BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Model model, RedirectAttributes redirectAttributes) {
        MultipartFile file = form.getFile();
        if (file == null || file.isEmpty()) {
            bindingResult.rejectValue("file", "required", "Vui lòng chọn tệp");
        }
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "documents/form";
        }

        CustomUserPrincipal currentUser = requirePrincipal(principal);
        String storedName = fileStorageService.store(file);
        Document document = new Document();
        document.setTitle(form.getTitle());
        document.setDescription(form.getDescription());
        document.setAuthorName(form.getAuthorName());
        document.setCategory(categoryService.findById(form.getCategoryId()));
        if (form.getDepartmentId() != null) {
            document.setDepartment(departmentRepository.findById(form.getDepartmentId()).orElse(null));
        }
        document.setFileName(resolveFileName(file));
        document.setFilePath(storedName);
        document.setFileType(resolveContentType(file));
        document.setFileSize(file.getSize());
        User owner = userService.findById(currentUser.getId());
        documentService.saveDraft(document, owner);
        redirectAttributes.addFlashAttribute("success", "Đã lưu tài liệu nháp");
        return "redirect:/documents";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Model model) {
        Document document = documentService.findById(id);
        verifyOwnerOrAdmin(document, requirePrincipal(principal));
        model.addAttribute("document", document);
        return "documents/detail";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        documentService.submit(id, userService.findById(requirePrincipal(principal).getId()));
        redirectAttributes.addFlashAttribute("success", "Đã gửi tài liệu để duyệt");
        return "redirect:/documents";
    }

    private void addReferenceData(Model model) {
        model.addAttribute("categories", categoryService.findActive());
        model.addAttribute("departments", departmentRepository.findByActiveTrueOrderByNameAsc());
    }

    private void verifyOwnerOrAdmin(Document document, CustomUserPrincipal principal) {
        boolean admin = principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(principal.getId()))) {
            throw new InvalidStatusException("Bạn không có quyền xem tài liệu này");
        }
    }

    private CustomUserPrincipal requirePrincipal(CustomUserPrincipal principal) {
        return Objects.requireNonNull(principal, "Bạn cần đăng nhập để thực hiện thao tác này");
    }

    private String resolveFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return (originalFilename == null || originalFilename.isBlank()) ? "uploaded-file" : originalFilename;
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }
}
