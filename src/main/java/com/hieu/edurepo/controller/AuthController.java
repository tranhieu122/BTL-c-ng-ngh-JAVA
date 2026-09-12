package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.RegisterForm;
import com.hieu.edurepo.dto.OtpForm;
import com.hieu.edurepo.dto.ResetPasswordForm;
import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.service.EmailService;
import com.hieu.edurepo.service.OtpService;
import com.hieu.edurepo.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private static final String PENDING_REGISTRATION = "PENDING_REGISTRATION";
    private static final String PENDING_RESET_EMAIL = "PENDING_RESET_EMAIL";
    private static final String VERIFIED_RESET_EMAIL = "VERIFIED_RESET_EMAIL";

    private final UserService userService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public AuthController(UserService userService, OtpService otpService, EmailService emailService,
                          PasswordEncoder passwordEncoder, com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.userService = userService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogs = auditLogs;
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
                           RedirectAttributes redirectAttributes,
                           HttpSession session) {
        if (!com.hieu.edurepo.util.PasswordPolicy.isValid(form.getPassword())) {
            bindingResult.rejectValue("password", "password.policy", com.hieu.edurepo.util.PasswordPolicy.MESSAGE);
        }
        if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Mật khẩu xác nhận không khớp");
        }
        if (bindingResult.hasErrors()) return "auth/register";
        try {
            if (userService.emailExists(form.getEmail())) {
                bindingResult.rejectValue("email", "email.exists", "Email này đã được đăng ký");
                return "auth/register";
            }
            PendingRegistration pending = new PendingRegistration(form.getFullName(), form.getEmail(),
                    passwordEncoder.encode(form.getPassword()));
            session.setAttribute(PENDING_REGISTRATION, pending);
            sendOtp(form.getEmail(), OtpPurpose.REGISTER, "dang ky tai khoan", redirectAttributes);
            return "redirect:/register/verify";
        } catch (IllegalStateException exception) {
            if ("EMAIL_EXISTS".equals(exception.getMessage())) {
                bindingResult.rejectValue("email", "email.exists", "Email này đã được đăng ký");
                return "auth/register";
            }
            model.addAttribute("errorMessage", exception.getMessage());
            return "auth/register";
        } catch (OtpService.OtpCooldownException exception) {
            redirectAttributes.addFlashAttribute("otpCooldownSeconds", exception.getSecondsRemaining());
            return "redirect:/register/verify";
        }
    }

    @GetMapping("/register/verify")
    public String verifyRegister(Model model, HttpSession session) {
        PendingRegistration pending = (PendingRegistration) session.getAttribute(PENDING_REGISTRATION);
        if (pending == null) return "redirect:/register";
        if (!model.containsAttribute("otpForm")) model.addAttribute("otpForm", new OtpForm());
        model.addAttribute("email", pending.email());
        return "auth/verify-register";
    }

    @PostMapping("/register/verify")
    public String verifyRegister(@Valid @ModelAttribute("otpForm") OtpForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        PendingRegistration pending = (PendingRegistration) session.getAttribute(PENDING_REGISTRATION);
        if (pending == null) return "redirect:/register";
        model.addAttribute("email", pending.email());
        if (bindingResult.hasErrors()) return "auth/verify-register";
        OtpService.OtpVerification verification = otpService.verify(pending.email(), OtpPurpose.REGISTER, form.getCode());
        if (verification != OtpService.OtpVerification.VALID) {
            bindingResult.rejectValue("code", "otp.invalid", otpMessage(verification));
            return "auth/verify-register";
        }
        try {
            var registeredUser = userService.registerWithEncodedPassword(pending.fullName(), pending.email(), pending.encodedPassword());
            auditLogs.recordAsUser(registeredUser, com.hieu.edurepo.enums.AuditAction.USER_REGISTERED,
                    com.hieu.edurepo.enums.AuditTargetType.USER, registeredUser.getId(),
                    "Đăng ký tài khoản mới", com.hieu.edurepo.enums.AuditResult.SUCCESS);
            session.removeAttribute(PENDING_REGISTRATION);
            redirectAttributes.addFlashAttribute("registered", true);
            return "redirect:/login";
        } catch (IllegalStateException exception) {
            if ("EMAIL_EXISTS".equals(exception.getMessage())) {
                session.removeAttribute(PENDING_REGISTRATION);
                redirectAttributes.addFlashAttribute("errorMessage", "Email này đã được đăng ký");
                return "redirect:/register";
            }
            throw exception;
        }
    }

    @PostMapping("/register/verify/resend")
    public String resendRegisterOtp(HttpSession session, RedirectAttributes redirectAttributes) {
        PendingRegistration pending = (PendingRegistration) session.getAttribute(PENDING_REGISTRATION);
        if (pending == null) return "redirect:/register";
        try {
            sendOtp(pending.email(), OtpPurpose.REGISTER, "dang ky tai khoan", redirectAttributes);
        } catch (OtpService.OtpCooldownException exception) {
            redirectAttributes.addFlashAttribute("otpCooldownSeconds", exception.getSecondsRemaining());
        }
        return "redirect:/register/verify";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() { return "auth/forgot-password"; }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, RedirectAttributes redirectAttributes,
                                 HttpSession session) {
        String normalizedEmail = email == null ? "" : email.trim();
        session.setAttribute(PENDING_RESET_EMAIL, normalizedEmail);
        if (userService.activeAccountExists(normalizedEmail)) {
            userService.requestPasswordReset(normalizedEmail);
            try {
                sendOtp(normalizedEmail, OtpPurpose.PASSWORD_RESET, "lay lai mat khau", redirectAttributes);
            } catch (OtpService.OtpCooldownException exception) {
                redirectAttributes.addFlashAttribute("otpCooldownSeconds", exception.getSecondsRemaining());
            }
        }
        redirectAttributes.addFlashAttribute("resetRequested", true);
        return "redirect:/forgot-password/verify";
    }

    @GetMapping("/forgot-password/verify")
    public String verifyReset(Model model, HttpSession session) {
        String email = (String) session.getAttribute(PENDING_RESET_EMAIL);
        if (email == null || email.isBlank()) return "redirect:/forgot-password";
        if (!model.containsAttribute("otpForm")) model.addAttribute("otpForm", new OtpForm());
        model.addAttribute("email", email);
        return "auth/verify-reset";
    }

    @PostMapping("/forgot-password/verify")
    public String verifyReset(@Valid @ModelAttribute("otpForm") OtpForm form,
                              BindingResult bindingResult,
                              Model model,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(PENDING_RESET_EMAIL);
        if (email == null || email.isBlank()) return "redirect:/forgot-password";
        model.addAttribute("email", email);
        if (bindingResult.hasErrors()) return "auth/verify-reset";
        OtpService.OtpVerification verification = otpService.verify(email, OtpPurpose.PASSWORD_RESET, form.getCode());
        if (verification != OtpService.OtpVerification.VALID) {
            bindingResult.rejectValue("code", "otp.invalid", otpMessage(verification));
            return "auth/verify-reset";
        }
        session.removeAttribute(PENDING_RESET_EMAIL);
        session.setAttribute(VERIFIED_RESET_EMAIL, email);
        return "redirect:/reset-password";
    }

    @PostMapping("/forgot-password/verify/resend")
    public String resendResetOtp(HttpSession session, RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(PENDING_RESET_EMAIL);
        if (email == null || email.isBlank()) return "redirect:/forgot-password";
        if (userService.activeAccountExists(email)) {
            try {
                sendOtp(email, OtpPurpose.PASSWORD_RESET, "lay lai mat khau", redirectAttributes);
            } catch (OtpService.OtpCooldownException exception) {
                redirectAttributes.addFlashAttribute("otpCooldownSeconds", exception.getSecondsRemaining());
            }
        }
        redirectAttributes.addFlashAttribute("resetRequested", true);
        return "redirect:/forgot-password/verify";
    }

    @GetMapping("/reset-password")
    public String resetPassword(Model model, HttpSession session) {
        if (session.getAttribute(VERIFIED_RESET_EMAIL) == null) return "redirect:/forgot-password";
        if (!model.containsAttribute("resetPasswordForm")) model.addAttribute("resetPasswordForm", new ResetPasswordForm());
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                BindingResult bindingResult,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(VERIFIED_RESET_EMAIL);
        if (email == null) return "redirect:/forgot-password";
        if (!com.hieu.edurepo.util.PasswordPolicy.isValid(form.getPassword())) {
            bindingResult.rejectValue("password", "password.policy", com.hieu.edurepo.util.PasswordPolicy.MESSAGE);
        }
        if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Mật khẩu xác nhận không khớp");
        }
        if (bindingResult.hasErrors()) return "auth/reset-password";
        userService.resetPassword(email, form.getPassword());
        session.removeAttribute(VERIFIED_RESET_EMAIL);
        redirectAttributes.addFlashAttribute("passwordReset", true);
        return "redirect:/login";
    }

    private void sendOtp(String email, OtpPurpose purpose, String label, RedirectAttributes redirectAttributes) {
        OtpService.OtpIssue issue = otpService.issue(email, purpose);
        boolean sent = emailService.sendOtp(email, issue.code(), label);
        redirectAttributes.addFlashAttribute(sent ? "otpSent" : "otpMailSkipped", true);
    }

    private String otpMessage(OtpService.OtpVerification verification) {
        return switch (verification) {
            case EXPIRED, MISSING -> "Mã OTP không đúng hoặc đã hết hạn. Hãy gửi lại mã mới.";
            case LOCKED -> "Bạn đã nhập sai quá nhiều lần. Hãy gửi lại mã mới.";
            default -> "Mã OTP không đúng";
        };
    }

    private record PendingRegistration(String fullName, String email, String encodedPassword)
            implements java.io.Serializable {
    }
}
