package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.dto.AuditLogFilter;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import com.hieu.edurepo.service.AuditLogService;
import com.hieu.edurepo.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
/**
 * Bộ điều hướng quản trị Nhật ký kiểm toán an ninh hệ thống (Admin Audit Log Controller).
 * Cung cấp tính năng tra cứu lịch sử hành vi người dùng, lọc theo hành động và IP.
 */
public class AdminAuditLogController {
    private static final int PAGE_SIZE = 25;

    private final AuditLogService auditLogs;
    private final UserService users;

    public AdminAuditLogController(AuditLogService auditLogs, UserService users) {
        this.auditLogs = auditLogs;
        this.users = users;
    }

    @GetMapping
    public String list(@ModelAttribute("filter") AuditLogFilter filter,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        var logs = auditLogs.search(filter, PageRequest.of(Math.max(0, page), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
        model.addAttribute("logs", logs);
        model.addAttribute("actions", AuditAction.values());
        model.addAttribute("roles", java.util.List.of("GUEST", "USER", "SUBMITTER", "REVIEWER", "ADMIN"));
        model.addAttribute("targetTypes", AuditTargetType.values());
        model.addAttribute("results", AuditResult.values());
        model.addAttribute("users", users.findAll());
        return "admin/audit-logs";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("log", auditLogs.findById(id));
        return "admin/audit-log-detail";
    }
}
