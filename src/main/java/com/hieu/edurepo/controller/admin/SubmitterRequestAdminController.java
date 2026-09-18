package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.SubmitterRequestService;
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
@RequestMapping("/admin/submitter-requests")
public class SubmitterRequestAdminController {
    private final SubmitterRequestService service;

    public SubmitterRequestAdminController(SubmitterRequestService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("requests", service.findAll());
        return "admin/submitter-requests";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @AuthenticationPrincipal CustomUserPrincipal principal,
                          RedirectAttributes attributes) {
        try {
            service.approve(id, requirePrincipal(principal).getId());
            attributes.addFlashAttribute("success", "Đã cấp quyền SUBMITTER cho người dùng.");
        } catch (InvalidStatusException exception) {
            attributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/submitter-requests";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam String rejectionReason,
                         @AuthenticationPrincipal CustomUserPrincipal principal,
                         RedirectAttributes attributes) {
        try {
            service.reject(id, requirePrincipal(principal).getId(), rejectionReason);
            attributes.addFlashAttribute("success", "Đã từ chối yêu cầu và lưu lý do.");
        } catch (InvalidStatusException | IllegalArgumentException exception) {
            attributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/submitter-requests";
    }

    private CustomUserPrincipal requirePrincipal(CustomUserPrincipal principal) {
        return Objects.requireNonNull(principal, "Bạn cần đăng nhập");
    }
}
