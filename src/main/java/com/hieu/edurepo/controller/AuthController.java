package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.RegisterForm;
import com.hieu.edurepo.dto.OtpForm;
import com.hieu.edurepo.dto.ResetPasswordForm;
import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.observability.OperationalMetrics;
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

/**
 * Controller xử lý toàn bộ luồng xác thực người dùng: đăng nhập, đăng ký,
 * quên mật khẩu và đặt lại mật khẩu qua OTP.
 *
 * <h3>Luồng đăng ký:</h3>
 * <ol>
 *   <li>Người dùng điền form đăng ký → validate → mã hóa mật khẩu → lưu vào session.</li>
 *   <li>Gửi OTP đến email → người dùng nhập mã.</li>
 *   <li>Nếu OTP hợp lệ → tạo tài khoản thật trong CSDL → chuyển đến trang đăng nhập.</li>
 * </ol>
 *
 * <h3>Luồng quên mật khẩu:</h3>
 * <ol>
 *   <li>Nhập email → gửi OTP (không tiết lộ email có tồn tại hay không).</li>
 *   <li>Nhập OTP → lưu trạng thái "đã xác thực" vào session.</li>
 *   <li>Đặt mật khẩu mới → xóa session → chuyển đến trang đăng nhập.</li>
 * </ol>
 *
 * <p>Mọi hành động thành công đều được ghi vào audit log thông qua {@code AuditLogService}.</p>
 */
@Controller
public class AuthController {
    // Các khóa session này lưu trạng thái tạm của luồng OTP.
    // Dữ liệu chỉ nằm trong phiên trình duyệt, không tạo user/reset password trước khi xác thực mã.
    private static final String PENDING_REGISTRATION = "PENDING_REGISTRATION";
    private static final String PENDING_RESET_EMAIL = "PENDING_RESET_EMAIL";
    private static final String VERIFIED_RESET_EMAIL = "VERIFIED_RESET_EMAIL";

    private final UserService userService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;
    private final OperationalMetrics metrics;

    public AuthController(UserService userService, OtpService otpService, EmailService emailService,
                          PasswordEncoder passwordEncoder, com.hieu.edurepo.service.AuditLogService auditLogs,
                          OperationalMetrics metrics) {
        this.userService = userService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogs = auditLogs;
        this.metrics = metrics;
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
            // Kiểm tra email trước khi gửi OTP để không tạo mã cho tài khoản chắc chắn bị từ chối.
            if (userService.emailExists(form.getEmail())) {
                bindingResult.rejectValue("email", "email.exists", "Email này đã được đăng ký");
                return "auth/register";
            }
            // Chưa lưu tài khoản vào database ở bước này.
            // Mật khẩu được mã hóa trước khi đưa vào session để tránh giữ plain text lâu hơn cần thiết.
            PendingRegistration pending = new PendingRegistration(form.getFullName(), form.getEmail(),
                    passwordEncoder.encode(form.getPassword()));
            session.setAttribute(PENDING_REGISTRATION, pending);
            sendOtp(form.getEmail(), OtpPurpose.REGISTER, redirectAttributes);
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
            // Chỉ khi OTP hợp lệ mới tạo user thật trong database.
            // Cách này tránh rác tài khoản chưa xác thực và bảo đảm email thuộc về người đăng ký.
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
            sendOtp(pending.email(), OtpPurpose.REGISTER, redirectAttributes);
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
        // Không báo khác nhau giữa email tồn tại và không tồn tại.
        // Đây là kỹ thuật chống dò tài khoản qua màn hình quên mật khẩu.
        if (userService.activeAccountExists(normalizedEmail)) {
            userService.requestPasswordReset(normalizedEmail);
            try {
                sendOtp(normalizedEmail, OtpPurpose.PASSWORD_RESET, redirectAttributes);
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
        // Sau khi OTP đúng, đổi trạng thái session từ "đang chờ OTP" sang "được phép đặt mật khẩu mới".
        // Người dùng truy cập thẳng /reset-password mà chưa qua bước này sẽ bị chuyển về quên mật khẩu.
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
                sendOtp(email, OtpPurpose.PASSWORD_RESET, redirectAttributes);
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
        com.hieu.edurepo.entity.User resetUser = userService.findByEmail(email);
        auditLogs.recordAsUser(resetUser, com.hieu.edurepo.enums.AuditAction.PASSWORD_RESET,
                com.hieu.edurepo.enums.AuditTargetType.USER, resetUser == null ? null : resetUser.getId(),
                "Đặt lại mật khẩu qua luồng OTP", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        session.removeAttribute(VERIFIED_RESET_EMAIL);
        redirectAttributes.addFlashAttribute("passwordReset", true);
        return "redirect:/login";
    }

    private void sendOtp(String email, OtpPurpose purpose, RedirectAttributes redirectAttributes) {
        // OtpService chỉ tạo và lưu hash OTP; EmailService mới chịu trách nhiệm gửi mã gốc cho người dùng.
        OtpService.OtpIssue issue = otpService.issue(email, purpose);
        EmailService.OtpDeliveryStatus deliveryStatus;
        try {
            deliveryStatus = emailService.sendOtpStatus(email, issue.code(), purpose);
        } catch (RuntimeException deliveryFailure) {
            otpService.revoke(issue);
            metrics.otpRevoked(purpose);
            throw deliveryFailure;
        }
        if (deliveryStatus != EmailService.OtpDeliveryStatus.SUCCESS) {
            // The user cannot use a code they never received. Revoking this exact issue also
            // removes its resend cooldown without affecting a newer concurrent OTP.
            otpService.revoke(issue);
            metrics.otpRevoked(purpose);
        }
        switch (deliveryStatus) {
            case SUCCESS -> redirectAttributes.addFlashAttribute("otpSent", true);
            case CONFIGURATION_ERROR -> redirectAttributes.addFlashAttribute("otpMailSkipped", true);
            case DELIVERY_FAILED -> redirectAttributes.addFlashAttribute("otpMailFailed", true);
        }
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
