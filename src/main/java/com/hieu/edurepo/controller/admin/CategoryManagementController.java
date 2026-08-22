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

@Controller
@RequestMapping("/admin/categories")
public class CategoryManagementController {

    private final CategoryService categoryService;

    public CategoryManagementController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("category", new Category());
        return "admin/categories";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("category") Category category, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderList(model);
        }
        try {
            categoryService.save(category);
        } catch (IllegalStateException exception) {
            if ("CATEGORY_EXISTS".equals(exception.getMessage())) {
                bindingResult.rejectValue("name", "name.exists", "Tên danh mục đã tồn tại");
                return renderList(model);
            }
            throw exception;
        }
        redirectAttributes.addFlashAttribute("success", "Đã lưu danh mục");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        categoryService.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Đã ngừng sử dụng danh mục");
        return "redirect:/admin/categories";
    }

    private String renderList(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "admin/categories";
    }
}
