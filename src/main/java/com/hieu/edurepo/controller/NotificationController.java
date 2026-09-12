package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.NotificationPage;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal CustomUserPrincipal principal, Model model,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
        model.addAttribute("notificationPage", notifications.list(principal.getId(), page, size));
        model.addAttribute("unreadCount", notifications.unreadCount(principal.getId()));
        return "notifications/index";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal CustomUserPrincipal principal,
                         @PathVariable Long id, Model model) {
        model.addAttribute("notification", notifications.findForRecipient(principal.getId(), id));
        model.addAttribute("unreadCount", notifications.unreadCount(principal.getId()));
        return "notifications/detail";
    }

    @PostMapping("/{id}/read")
    public String markRead(@AuthenticationPrincipal CustomUserPrincipal principal,
                           @PathVariable Long id, RedirectAttributes redirectAttributes) {
        notifications.markRead(principal.getId(), id);
        redirectAttributes.addFlashAttribute("success", "Đã đánh dấu thông báo là đã đọc");
        return "redirect:/notifications/" + id;
    }

    @PostMapping("/read-all")
    public String markAllReadPage(@AuthenticationPrincipal CustomUserPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        notifications.markAllRead(principal.getId());
        redirectAttributes.addFlashAttribute("success", "Đã đánh dấu tất cả thông báo là đã đọc");
        return "redirect:/notifications";
    }

    @GetMapping("/api")
    @ResponseBody
    public NotificationPage list(@AuthenticationPrincipal CustomUserPrincipal principal,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return notifications.list(principal.getId(), page, size);
    }

    @GetMapping("/api/unread-count")
    @ResponseBody
    public Map<String, Long> unreadCount(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return Map.of("count", notifications.unreadCount(principal.getId()));
    }

    @PostMapping("/api/{id}/read")
    @ResponseBody
    public ResponseEntity<Void> markReadApi(@AuthenticationPrincipal CustomUserPrincipal principal,
                                            @PathVariable Long id) {
        notifications.markRead(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/read-all")
    @ResponseBody
    public ResponseEntity<Void> markAllReadApi(@AuthenticationPrincipal CustomUserPrincipal principal) {
        notifications.markAllRead(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
