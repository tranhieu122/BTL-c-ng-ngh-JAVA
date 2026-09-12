package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public AdminDashboardController(DocumentRepository documentRepository,
                                    UserRepository userRepository,
                                    com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.auditLogs = auditLogs;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalDocuments = documentRepository.count();
        long draftCount = documentRepository.countByStatus(DocumentStatus.DRAFT);
        long submittedCount = documentRepository.countByStatus(DocumentStatus.SUBMITTED);
        long approvedCount = documentRepository.countByStatus(DocumentStatus.APPROVED);
        long publishedCount = documentRepository.countByStatus(DocumentStatus.PUBLISHED);
        long revisionCount = documentRepository.countByStatus(DocumentStatus.REVISION_REQUIRED);
        long rejectedCount = documentRepository.countByStatus(DocumentStatus.REJECTED);

        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("documentCount", totalDocuments);
        model.addAttribute("draftCount", draftCount);
        model.addAttribute("pendingCount", submittedCount);
        model.addAttribute("publishedCount", publishedCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("revisionCount", revisionCount);
        model.addAttribute("rejectedCount", rejectedCount);
        model.addAttribute("publicationRate", percent(publishedCount, totalDocuments));
        model.addAttribute("reviewRate", percent(approvedCount + publishedCount + rejectedCount + revisionCount, totalDocuments));
        model.addAttribute("pendingRate", percent(submittedCount, totalDocuments));
        model.addAttribute("approvedRate", percent(approvedCount, totalDocuments));
        model.addAttribute("categoryCounts", documentRepository.countByCategory());
        model.addAttribute("facultyCounts", documentRepository.countByFaculty());
        model.addAttribute("recentDocuments", documentRepository.findTop8ByOrderByCreatedAtDesc());
        model.addAttribute("totalViews", documentRepository.sumViewCount());
        model.addAttribute("totalDownloads", documentRepository.sumDownloadCount());
        model.addAttribute("popularDocuments", documentRepository
                .findTop8ByStatusOrderByDownloadCountDescViewCountDescPublishedAtDesc(DocumentStatus.PUBLISHED));
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.ADMIN_DASHBOARD_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.PAGE, null, "Dashboard quản trị",
                "Xem dashboard quản trị", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "admin/dashboard";
    }

    private long percent(long value, long total) {
        return total == 0 ? 0 : Math.round(value * 100.0 / total);
    }
}
