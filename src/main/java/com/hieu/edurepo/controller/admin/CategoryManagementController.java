package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Bộ điều hướng quản trị Danh mục phân loại học liệu (Category Management Controller).
 * Hỗ trợ thêm mới, chỉnh sửa tên và ẩn/hiện danh mục tài liệu đào tạo.
 */
@Controller
@RequestMapping("/admin/categories")
public class CategoryManagementController {

    private final CategoryService categoryService;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public CategoryManagementController(CategoryService categoryService,
                                        com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.categoryService = categoryService;
        this.auditLogs = auditLogs;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("category", new Category());
        audit(com.hieu.edurepo.enums.AuditAction.CATEGORY_MANAGEMENT_VIEWED, null,
                "Quản lý danh mục", "Xem trang quản lý danh mục");
        return "admin/categories";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("category") Category category, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        category.setId(null);
        if (bindingResult.hasErrors()) {
            return renderList(model);
        }
        try {
            category = categoryService.save(category);
        } catch (IllegalStateException exception) {
            if ("CATEGORY_EXISTS".equals(exception.getMessage())) {
                bindingResult.rejectValue("name", "name.exists", "Tên danh mục đã tồn tại");
                return renderList(model);
            }
            throw exception;
        }
        audit(com.hieu.edurepo.enums.AuditAction.CATEGORY_CREATED, category.getId(), category.getName(),
                "Thêm danh mục: " + category.getName());
        redirectAttributes.addFlashAttribute("success", "Đã lưu danh mục");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Category category = categoryService.findById(id);
        categoryService.deleteById(id);
        audit(com.hieu.edurepo.enums.AuditAction.CATEGORY_STATUS_CHANGED, id, category.getName(),
                "Ngừng sử dụng danh mục: " + category.getName());
        redirectAttributes.addFlashAttribute("success", "Đã ngừng sử dụng danh mục");
        return "redirect:/admin/categories";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("category", categoryService.findById(id));
        return renderList(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("category") Category changes,
                         BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        Category current = categoryService.findById(id);
        changes.setId(id);
        if (result.hasErrors()) return renderList(model);
        current.setName(changes.getName());
        current.setDescription(changes.getDescription());
        try { categoryService.save(current); }
        catch (IllegalStateException exception) {
            if (!"CATEGORY_EXISTS".equals(exception.getMessage())) throw exception;
            result.rejectValue("name", "exists", "Tên danh mục đã tồn tại");
            return renderList(model);
        }
        audit(com.hieu.edurepo.enums.AuditAction.CATEGORY_UPDATED, id, current.getName(),
                "Cập nhật danh mục: " + current.getName());
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật danh mục");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Category category = categoryService.findById(id);
        category.setActive(true);
        categoryService.save(category);
        audit(com.hieu.edurepo.enums.AuditAction.CATEGORY_STATUS_CHANGED, id, category.getName(),
                "Kích hoạt danh mục: " + category.getName());
        redirectAttributes.addFlashAttribute("success", "Đã kích hoạt danh mục");
        return "redirect:/admin/categories";
    }

    private String renderList(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "admin/categories";
    }

    private void audit(com.hieu.edurepo.enums.AuditAction action, Long id, String name, String description) {
        auditLogs.record(action, com.hieu.edurepo.enums.AuditTargetType.CATEGORY, id, name, description,
                com.hieu.edurepo.enums.AuditResult.SUCCESS);
    }
}
