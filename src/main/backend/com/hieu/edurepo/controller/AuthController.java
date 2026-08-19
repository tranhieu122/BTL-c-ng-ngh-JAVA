package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.RegisterForm;
import com.hieu.edurepo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() { return "auth/login"; }

    @GetMapping("/access-denied")
    public String accessDenied() { return "auth/access-denied"; }

    @GetMapping("/register")
    public String register(Model model) {
        if (!model.containsAttribute("registerForm")) model.addAttribute("registerForm", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                           BindingResult bindingResult, Model model,
                           RedirectAttributes redirectAttributes) {
        if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Mật khẩu xác nhận không khớp");
        }
        if (bindingResult.hasErrors()) return "auth/register";
        try {
            userService.register(form.getFullName(), form.getEmail(), form.getPassword());
        } catch (IllegalStateException exception) {
            if ("EMAIL_EXISTS".equals(exception.getMessage())) {
                bindingResult.rejectValue("email", "email.exists", "Email này đã được đăng ký");
                return "auth/register";
            }
            model.addAttribute("errorMessage", exception.getMessage());
            return "auth/register";
        }
        redirectAttributes.addFlashAttribute("registered", true);
        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() { return "auth/forgot-password"; }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, RedirectAttributes redirectAttributes) {
        // SMTP chưa cấu hình: luôn trả thông báo chung để không làm lộ tài khoản tồn tại.
        redirectAttributes.addFlashAttribute("resetRequested", true);
        return "redirect:/forgot-password";
    }
}
