package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.dto.AdminNotificationForm;
import com.hieu.edurepo.enums.NotificationLevel;
import com.hieu.edurepo.enums.NotificationTargetType;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {
    private final NotificationService notifications;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public AdminNotificationController(NotificationService notifications,
                                       com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.notifications = notifications;
        this.auditLogs = auditLogs;
    }

    @GetMapping
    public String createForm(Model model) {
        if (!model.containsAttribute("notificationForm")) {
            model.addAttribute("notificationForm", new AdminNotificationForm());
        }
        return render(model);
    }

    @PostMapping
    public String send(@AuthenticationPrincipal CustomUserPrincipal principal,
                       @Valid @ModelAttribute("notificationForm") AdminNotificationForm form,
                       BindingResult bindingResult,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        validateTarget(form, bindingResult);
        if (bindingResult.hasErrors()) {
            return render(model);
        }

        try {
            int sent = notifications.sendFromAdmin(principal.getId(), form);
            auditLogs.record(com.hieu.edurepo.enums.AuditAction.SYSTEM_NOTIFICATION_SENT,
                    com.hieu.edurepo.enums.AuditTargetType.NOTIFICATION, null,
                    form.getTitle().trim(),
                    "Gửi thông báo hệ thống ‘" + form.getTitle().trim() + "’ tới " + sent + " người nhận",
                    com.hieu.edurepo.enums.AuditResult.SUCCESS);
            redirectAttributes.addFlashAttribute("success",
                    sent == 0 ? "Không có người dùng phù hợp để nhận thông báo" : "Gửi thông báo thành công");
            return "redirect:/admin/notifications";
        } catch (IllegalArgumentException exception) {
            auditLogs.record(com.hieu.edurepo.enums.AuditAction.SYSTEM_NOTIFICATION_SENT,
                    com.hieu.edurepo.enums.AuditTargetType.NOTIFICATION, null,
                    "Không thể gửi thông báo hệ thống vì chưa có người nhận phù hợp",
                    com.hieu.edurepo.enums.AuditResult.FAILURE);
            bindingResult.rejectValue("targetType", "receiver.required", "Vui lòng chọn đối tượng nhận");
            return render(model);
        }
    }

    private void validateTarget(AdminNotificationForm form, BindingResult bindingResult) {
        if (form.getTargetType() == null) {
            return;
        }
        if (form.getTargetType() == NotificationTargetType.ROLE && form.getRole() == null) {
            bindingResult.rejectValue("role", "role.required", "Vui lòng chọn vai trò nhận thông báo");
        }
        if (form.getTargetType() == NotificationTargetType.USER && form.getReceiverId() == null) {
            bindingResult.rejectValue("receiverId", "receiver.required", "Vui lòng chọn người dùng nhận thông báo");
        }
    }

    private String render(Model model) {
        model.addAttribute("targetTypes", NotificationTargetType.values());
        model.addAttribute("notificationLevels", NotificationLevel.values());
        model.addAttribute("roleNames", RoleName.values());
        model.addAttribute("users", notifications.activeUsers());
        return "admin/notifications";
    }
}
