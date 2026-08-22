package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentForm;
import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Department;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Faculty;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.DepartmentRepository;
import com.hieu.edurepo.repository.FacultyRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.CategoryService;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.ReviewService;
import com.hieu.edurepo.service.UserService;
import com.hieu.edurepo.util.FileValidationUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final DepartmentRepository departmentRepository;
    private final FacultyRepository facultyRepository;
    private final FileStorageService fileStorageService;
    private final ReviewService reviewService;

    public DocumentController(DocumentService documentService, UserService userService,
            CategoryService categoryService, DepartmentRepository departmentRepository,
            FacultyRepository facultyRepository, FileStorageService fileStorageService, ReviewService reviewService) {
        this.documentService = documentService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.departmentRepository = departmentRepository;
        this.facultyRepository = facultyRepository;
        this.fileStorageService = fileStorageService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public String myDocuments(@AuthenticationPrincipal CustomUserPrincipal principal, Model model,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long departmentId, @RequestParam(defaultValue = "0") int page) {
        Page<Document> documentPage = documentService.searchByOwner(requirePrincipal(principal).getId(), keyword,
                status, categoryId, facultyId, departmentId,
                PageRequest.of(Math.max(page, 0), 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("documents", documentPage.getContent());
        model.addAttribute("documentPage", documentPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedFacultyId", facultyId);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute("statuses", DocumentStatus.values());
        addReferenceData(model);
        return "documents/my-documents";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("documentForm", new DocumentForm());
        addReferenceData(model);
        return "documents/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("documentForm") DocumentForm form, BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Model model, RedirectAttributes redirectAttributes) {
        MultipartFile file = form.getFile();
        if (file == null || file.isEmpty()) {
            bindingResult.rejectValue("file", "required", "Vui lòng chọn tệp");
        }

        Category category = resolveCategory(form.getCategoryId(), bindingResult);
        Faculty faculty = resolveFaculty(form.getFacultyId(), bindingResult);
        Department department = resolveDepartment(form.getDepartmentId(), faculty, bindingResult);
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "documents/form";
        }

        CustomUserPrincipal currentUser = requirePrincipal(principal);
        User owner = userService.findById(currentUser.getId());
        MultipartFile validatedFile = Objects.requireNonNull(file);
        String storedName;
        try {
            storedName = fileStorageService.store(validatedFile);
        } catch (FileStorageException exception) {
            bindingResult.rejectValue("file", "storage", exception.getMessage());
            addReferenceData(model);
            return "documents/form";
        }

        Document document = new Document();
        document.setTitle(form.getTitle());
        document.setDescription(form.getDescription());
        document.setAuthorName(form.getAuthorName());
        document.setCategory(category);
        document.setDepartment(department);
        document.setFileName(resolveFileName(validatedFile));
        document.setFilePath(storedName);
        document.setFileType(resolveContentType(validatedFile));
        document.setFileSize(validatedFile.getSize());
        try {
            documentService.submitNew(document, owner);
        } catch (RuntimeException exception) {
            try {
                fileStorageService.delete(storedName);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        redirectAttributes.addFlashAttribute("success", "Đã nộp tài liệu thành công. Tài liệu đang chờ duyệt.");
        return "redirect:/documents";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Model model) {
        Document document = documentService.findById(id);
        verifyOwnerOrAdmin(document, requirePrincipal(principal));
        model.addAttribute("document", document);
        model.addAttribute("history", reviewService.history(id));
        return "documents/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Model model) {
        Document document = documentService.findById(id);
        verifyOwner(document, requirePrincipal(principal));
        verifyEditable(document);

        DocumentForm form = new DocumentForm();
        form.setTitle(document.getTitle());
        form.setDescription(document.getDescription());
        form.setAuthorName(document.getAuthorName());
        form.setCategoryId(document.getCategory() == null ? null : document.getCategory().getId());
        form.setFacultyId(document.getDepartment() == null || document.getDepartment().getFaculty() == null
                ? null : document.getDepartment().getFaculty().getId());
        form.setDepartmentId(document.getDepartment() == null ? null : document.getDepartment().getId());
        model.addAttribute("documentForm", form);
        addEditData(model, document);
        return "documents/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
            @Valid @ModelAttribute("documentForm") DocumentForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Model model, RedirectAttributes redirectAttributes) {
        CustomUserPrincipal currentPrincipal = requirePrincipal(principal);
        Document current = documentService.findById(id);
        verifyOwner(current, currentPrincipal);
        verifyEditable(current);

        Category category = resolveCategory(form.getCategoryId(), bindingResult);
        Faculty faculty = resolveFaculty(form.getFacultyId(), bindingResult);
        Department department = resolveDepartment(form.getDepartmentId(), faculty, bindingResult);
        if (bindingResult.hasErrors()) {
            addEditData(model, current);
            return "documents/form";
        }

        Document changes = documentChanges(form, category, department);
        MultipartFile replacement = form.getFile();
        String storedName = null;
        String oldStoredName = current.getFilePath();
        if (replacement != null && !replacement.isEmpty()) {
            try {
                storedName = fileStorageService.store(replacement);
                applyFileMetadata(changes, replacement, storedName);
            } catch (FileStorageException exception) {
                bindingResult.rejectValue("file", "storage", exception.getMessage());
                addEditData(model, current);
                return "documents/form";
            }
        }

        try {
            documentService.updateDraft(id, changes, userService.findById(currentPrincipal.getId()));
        } catch (RuntimeException exception) {
            deleteQuietly(storedName, "tệp thay thế sau khi cập nhật thất bại");
            throw exception;
        }

        if (storedName != null && oldStoredName != null
                && !oldStoredName.equals(storedName)) {
            deleteQuietly(oldStoredName, "tệp cũ sau khi thay thế");
        }
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật tài liệu");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        documentService.submit(id, userService.findById(requirePrincipal(principal).getId()));
        redirectAttributes.addFlashAttribute("success", "Đã gửi tài liệu để duyệt");
        return "redirect:/documents";
    }

    @PostMapping("/{id}/delete")
    public String deleteDraft(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        CustomUserPrincipal currentPrincipal = requirePrincipal(principal);
        Document document = documentService.findById(id);
        verifyOwner(document, currentPrincipal);
        String storedName = document.getFilePath();
        documentService.deleteDraft(id, userService.findById(currentPrincipal.getId()));
        deleteQuietly(storedName, "tệp của bản nháp đã xóa");
        redirectAttributes.addFlashAttribute("success", "Đã xóa bản nháp");
        return "redirect:/documents";
    }

    private void addReferenceData(Model model) {
        model.addAttribute("categories", categoryService.findActive());
        model.addAttribute("faculties", facultyRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("departments", departmentRepository.findByActiveTrueOrderByNameAsc());
    }

    private void addEditData(Model model, Document document) {
        addReferenceData(model);
        model.addAttribute("editing", true);
        model.addAttribute("documentId", document.getId());
        model.addAttribute("documentStatus", document.getStatus());
        model.addAttribute("currentFileName", document.getFileName());
    }

    private Document documentChanges(DocumentForm form, Category category, Department department) {
        Document changes = new Document();
        changes.setTitle(form.getTitle());
        changes.setDescription(form.getDescription());
        changes.setAuthorName(form.getAuthorName());
        changes.setCategory(category);
        changes.setDepartment(department);
        return changes;
    }

    private void applyFileMetadata(Document document, MultipartFile file, String storedName) {
        document.setFileName(resolveFileName(file));
        document.setFilePath(storedName);
        document.setFileType(resolveContentType(file));
        document.setFileSize(file.getSize());
    }

    private Category resolveCategory(Long categoryId, BindingResult bindingResult) {
        if (categoryId == null) {
            return null;
        }
        try {
            Category category = categoryService.findById(categoryId);
            if (!category.isActive()) {
                bindingResult.rejectValue("categoryId", "inactive", "Danh mục đã ngừng sử dụng");
                return null;
            }
            return category;
        } catch (ResourceNotFoundException exception) {
            bindingResult.rejectValue("categoryId", "notFound", "Danh mục không tồn tại");
            return null;
        }
    }

    private Faculty resolveFaculty(Long facultyId, BindingResult bindingResult) {
        if (facultyId == null) {
            return null;
        }
        Faculty faculty = facultyRepository.findById(facultyId).orElse(null);
        if (faculty == null || !faculty.isActive()) {
            bindingResult.rejectValue("facultyId", "notFound", "Khoa không hợp lệ hoặc đã ngừng sử dụng");
            return null;
        }
        return faculty;
    }

    private Department resolveDepartment(Long departmentId, Faculty faculty, BindingResult bindingResult) {
        if (departmentId == null) {
            return null;
        }
        Department department = departmentRepository.findById(departmentId).orElse(null);
        if (department == null || !department.isActive()) {
            bindingResult.rejectValue("departmentId", "notFound", "Bộ môn không hợp lệ hoặc đã ngừng sử dụng");
            return null;
        }
        if (faculty == null || department.getFaculty() == null
                || !department.getFaculty().getId().equals(faculty.getId())) {
            bindingResult.rejectValue("departmentId", "facultyMismatch", "Bộ môn không thuộc khoa đã chọn");
            return null;
        }
        return department;
    }

    private void verifyOwnerOrAdmin(Document document, CustomUserPrincipal principal) {
        boolean admin = principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(principal.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền xem tài liệu này");
        }
    }

    private void verifyOwner(Document document, CustomUserPrincipal principal) {
        if (document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi tài liệu này");
        }
    }

    private void verifyEditable(Document document) {
        if (document.getStatus() != DocumentStatus.DRAFT
                && document.getStatus() != DocumentStatus.REVISION_REQUIRED) {
            throw new InvalidStatusException("Tài liệu ở trạng thái hiện tại không thể chỉnh sửa");
        }
    }

    private void deleteQuietly(String storedName, String context) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            fileStorageService.delete(storedName);
        } catch (RuntimeException exception) {
            LOGGER.warn("Không thể xóa {}: {}", context, storedName, exception);
        }
    }

    private CustomUserPrincipal requirePrincipal(CustomUserPrincipal principal) {
        return Objects.requireNonNull(principal, "Bạn cần đăng nhập để thực hiện thao tác này");
    }

    private String resolveFileName(MultipartFile file) {
        return FileValidationUtil.getSafeOriginalFileName(file);
    }

    private String resolveContentType(MultipartFile file) {
        return switch (FileValidationUtil.validateAndGetExtension(file)) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> throw new FileStorageException("Định dạng tệp không hợp lệ");
        };
    }
}
