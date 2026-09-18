package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentForm;
import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Department;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Faculty;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.ReviewAction;
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
import com.hieu.edurepo.service.AuditLogService;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import com.hieu.edurepo.util.FileValidationUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AuditLogService auditLogs;

    public DocumentController(DocumentService documentService, UserService userService,
            CategoryService categoryService, DepartmentRepository departmentRepository,
            FacultyRepository facultyRepository, FileStorageService fileStorageService, ReviewService reviewService) {
        this(documentService, userService, categoryService, departmentRepository, facultyRepository,
                fileStorageService, reviewService, null);
    }

    @Autowired
    public DocumentController(DocumentService documentService, UserService userService,
            CategoryService categoryService, DepartmentRepository departmentRepository,
            FacultyRepository facultyRepository, FileStorageService fileStorageService, ReviewService reviewService,
            AuditLogService auditLogs) {
        this.documentService = documentService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.departmentRepository = departmentRepository;
        this.facultyRepository = facultyRepository;
        this.fileStorageService = fileStorageService;
        this.reviewService = reviewService;
        this.auditLogs = auditLogs;
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
        DocumentForm form = new DocumentForm();
        form.setLicenseType(com.hieu.edurepo.enums.LicenseType.ALL_RIGHTS_RESERVED);
        model.addAttribute("documentForm", form);
        addReferenceData(model);
        return "documents/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("documentForm") DocumentForm form, BindingResult bindingResult,
            @RequestParam(defaultValue = "submit") String intent,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            Model model, RedirectAttributes redirectAttributes) {
        // Tạo mới luôn cần file học liệu. Các lần chỉnh sửa sau có thể giữ file cũ nếu không upload lại.
        MultipartFile file = form.getFile();
        if (file == null || file.isEmpty()) {
            bindingResult.rejectValue("file", "required", "Vui lòng chọn tệp");
        }

        // Kiểm tra dữ liệu tham chiếu từ form trước khi lưu file để tránh có file rác nếu category/khoa sai.
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
            // Lỗi file được gắn vào đúng field để giao diện hiển thị ngay cạnh ô upload.
            bindingResult.rejectValue("file", "storage", exception.getMessage());
            addReferenceData(model);
            return "documents/form";
        }

        Document document = new Document();
        document.setTitle(form.getTitle());
        document.setDescription(form.getDescription());
        applyAcademicMetadata(document, form);
        if (document.getLicenseType() == null) document.setLicenseType(com.hieu.edurepo.enums.LicenseType.ALL_RIGHTS_RESERVED);
        document.setAuthorName(form.getAuthorName());
        document.setCategory(category);
        document.setDepartment(department);
        document.setFileName(resolveFileName(validatedFile));
        document.setFilePath(storedName);
        document.setFileType(resolveContentType(validatedFile));
        document.setFileSize(validatedFile.getSize());
        try {
            Document saved;
            // "draft" lưu bản nháp; mọi thao tác "submit" đều đi vào workflow kiểm duyệt.
            if ("draft".equals(intent)) {
                saved = documentService.saveDraft(document, owner);
                redirectAttributes.addFlashAttribute("success", "Đã lưu bản nháp trong tài khoản của bạn");
            } else {
                saved = documentService.submitNew(document, owner);
                saved = saved == null ? document : saved;
                redirectAttributes.addFlashAttribute("success", submissionMessage(saved));
            }
            saved = saved == null ? document : saved;
            audit(AuditAction.DOCUMENT_UPLOADED, saved.getId(), saved.getTitle(),
                    "Upload tài liệu: " + saved.getTitle());
            audit(AuditAction.DOCUMENT_CREATED, saved.getId(), saved.getTitle(),
                    "Tạo tài liệu: " + saved.getTitle());
            if ("draft".equals(intent)) {
                audit(AuditAction.DOCUMENT_DRAFT_SAVED, saved.getId(), saved.getTitle(),
                        "Lưu bản nháp tài liệu: " + saved.getTitle());
            }
            redirectAttributes.addFlashAttribute("clearSubmissionDraft", true);
        } catch (RuntimeException exception) {
            // File đã được chép lên disk trước khi ghi DB.
            // Nếu DB/service lỗi thì xóa file vừa upload để metadata và thư mục lưu trữ không lệch nhau.
            try {
                fileStorageService.delete(storedName);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        return "redirect:/documents";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") int historyPage,
            @RequestParam(defaultValue = "0") int versionPage,
            Model model) {
        Document document = documentService.findById(id);
        verifyOwnerOrAdmin(document, requirePrincipal(principal));
        var history = reviewService.history(id, PageRequest.of(Math.max(0, historyPage), 10));
        var versions = documentService.findVersions(id, PageRequest.of(Math.max(0, versionPage), 10));
        model.addAttribute("document", document);
        model.addAttribute("history", history.getContent());
        model.addAttribute("historyPage", history);
        model.addAttribute("versions", versions.getContent());
        model.addAttribute("versionPage", versions);
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
        form.setSummary(document.getSummary());
        form.setKeywords(document.getKeywords());
        form.setLanguageCode(document.getLanguageCode());
        form.setLearningResourceType(document.getLearningResourceType());
        form.setEducationLevel(document.getEducationLevel());
        form.setLicenseType(document.getLicenseType());
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

        // Khi sửa tài liệu, vẫn kiểm tra category/khoa/bộ môn vì admin có thể đã ngừng dùng dữ liệu tham chiếu.
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
        if (replacement != null && !replacement.isEmpty()) {
            try {
                // Chỉ tạo phiên bản file mới khi người dùng thật sự upload file thay thế.
                storedName = fileStorageService.store(replacement);
                applyFileMetadata(changes, replacement, storedName);
            } catch (FileStorageException exception) {
                bindingResult.rejectValue("file", "storage", exception.getMessage());
                addEditData(model, current);
                return "documents/form";
            }
        }

        try {
            User owner = userService.findById(currentPrincipal.getId());
            Document saved = form.getChangeNote() == null || form.getChangeNote().isBlank()
                    ? documentService.updateDraft(id, changes, owner)
                    : documentService.updateDraft(id, changes, owner, form.getChangeNote());
            saved = saved == null ? changes : saved;
            audit(AuditAction.DOCUMENT_UPDATED, saved.getId(), "Cập nhật tài liệu: " + saved.getTitle());
        } catch (RuntimeException exception) {
            deleteQuietly(storedName, "tệp thay thế sau khi cập nhật thất bại");
            throw exception;
        }

        redirectAttributes.addFlashAttribute("success", "Đã cập nhật tài liệu");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        User owner = userService.findById(requirePrincipal(principal).getId());
        Document submitted = documentService.submit(id, owner);
        redirectAttributes.addFlashAttribute("success", submissionMessage(submitted));
        return "redirect:/documents";
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public String publish(@PathVariable Long id,
                          @AuthenticationPrincipal CustomUserPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        User admin = userService.findById(requirePrincipal(principal).getId());
        reviewService.review(id, ReviewAction.PUBLISHED, "Công bố tài liệu", admin);
        redirectAttributes.addFlashAttribute("success", "Đã công bố tài liệu");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public String archive(@PathVariable Long id,
                          @AuthenticationPrincipal CustomUserPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        User admin = userService.findById(requirePrincipal(principal).getId());
        reviewService.review(id, ReviewAction.ARCHIVED, "Lưu trữ tài liệu", admin);
        redirectAttributes.addFlashAttribute("success", "Đã chuyển tài liệu vào kho lưu trữ");
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteDraft(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            RedirectAttributes redirectAttributes) {
        CustomUserPrincipal currentPrincipal = requirePrincipal(principal);
        Document document = documentService.findById(id);
        verifyOwner(document, currentPrincipal);
        var storedNames = documentService.versionFilePaths(id);
        if (storedNames.isEmpty() && document.getFilePath() != null) storedNames = java.util.List.of(document.getFilePath());
        documentService.deleteDraft(id, userService.findById(currentPrincipal.getId()));
        audit(AuditAction.DOCUMENT_DRAFT_DELETED, id, "Xóa bản nháp tài liệu: " + document.getTitle());
        storedNames.forEach(storedName -> deleteQuietly(storedName, "tệp của bản nháp đã xóa"));
        redirectAttributes.addFlashAttribute("success", "Đã xóa bản nháp");
        return "redirect:/documents";
    }

    private void addReferenceData(Model model) {
        model.addAttribute("categories", categoryService.findActive());
        model.addAttribute("faculties", facultyRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("departments", departmentRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("resourceTypes", LearningResourceType.values());
        model.addAttribute("educationLevels", EducationLevel.values());
        model.addAttribute("licenseTypes", com.hieu.edurepo.enums.LicenseType.values());
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
        applyAcademicMetadata(changes, form);
        changes.setAuthorName(form.getAuthorName());
        changes.setCategory(category);
        changes.setDepartment(department);
        return changes;
    }

    private void applyAcademicMetadata(Document document, DocumentForm form) {
        document.setSummary(form.getSummary());
        document.setKeywords(form.getKeywords());
        document.setLanguageCode(form.getLanguageCode() == null || form.getLanguageCode().isBlank()
                ? "vi" : form.getLanguageCode().trim().toLowerCase(java.util.Locale.ROOT));
        document.setLearningResourceType(form.getLearningResourceType());
        document.setEducationLevel(form.getEducationLevel());
        // A legacy POST without licenseType must not silently overwrite the license.
        document.setLicenseType(form.getLicenseType());
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
            // Không tin hoàn toàn dữ liệu hidden/select từ trình duyệt; luôn xác nhận bộ môn thuộc khoa đã chọn.
            bindingResult.rejectValue("departmentId", "facultyMismatch", "Bộ môn không thuộc khoa đã chọn");
            return null;
        }
        return department;
    }

    private void verifyOwnerOrAdmin(Document document, CustomUserPrincipal principal) {
        // Trang chi tiết nội bộ cho phép admin hỗ trợ kiểm tra, còn người dùng thường chỉ xem tài liệu của mình.
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
        // Sau khi đã gửi duyệt/công bố/từ chối, tác giả không được sửa trực tiếp để bảo toàn lịch sử xét duyệt.
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
            LOGGER.warn("Không thể xóa {} (exception={})", context, exception.getClass().getSimpleName());
            LOGGER.debug("Chi tiết lỗi cleanup tệp", exception);
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

    private String submissionMessage(Document document) {
        return document.getStatus() == DocumentStatus.RESUBMITTED
                ? "Đã gửi lại phiên bản chỉnh sửa. Tài liệu đang chờ reviewer kiểm tra."
                : "Đã nộp tài liệu thành công. Tài liệu đang chờ duyệt.";
    }

    private void audit(AuditAction action, Long documentId, String description) {
        audit(action, documentId, null, description);
    }

    private void audit(AuditAction action, Long documentId, String documentName, String description) {
        if (auditLogs != null) {
            auditLogs.record(action, AuditTargetType.DOCUMENT, documentId, documentName, description, AuditResult.SUCCESS);
        }
    }
}
