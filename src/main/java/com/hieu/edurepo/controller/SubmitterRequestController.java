package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.SubmitterRequestForm;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.SubmitterRequestService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
public class SubmitterRequestController {
    private final SubmitterRequestService service;

    public SubmitterRequestController(SubmitterRequestService service) {
        this.service = service;
    }

    @GetMapping("/submitter-request")
    public String page(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        Long userId = requirePrincipal(principal).getId();
        if (!model.containsAttribute("submitterRequestForm")) {
            model.addAttribute("submitterRequestForm", new SubmitterRequestForm());
        }
        model.addAttribute("requests", service.findForUser(userId));
        model.addAttribute("hasPendingRequest", service.hasPending(userId));
        model.addAttribute("alreadySubmitter", principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUBMITTER")));
        return "submitter-request/index";
    }

    @PostMapping("/submitter-request")
    public String create(@Valid @ModelAttribute SubmitterRequestForm submitterRequestForm,
                         BindingResult result,
                         @AuthenticationPrincipal CustomUserPrincipal principal,
                         Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) return page(principal, model);
        try {
            service.create(requirePrincipal(principal).getId(), submitterRequestForm.getReason());
            redirectAttributes.addFlashAttribute("success", "Yêu cầu quyền nộp tài liệu đã được gửi.");
        } catch (InvalidStatusException | IllegalArgumentException exception) {
            result.reject("submitter.request", exception.getMessage());
            return page(principal, model);
        }
        return "redirect:/submitter-request";
    }

    private CustomUserPrincipal requirePrincipal(CustomUserPrincipal principal) {
        return Objects.requireNonNull(principal, "Bạn cần đăng nhập");
    }
}
