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

    public AdminDashboardController(DocumentRepository documentRepository,
                                    UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalDocuments = documentRepository.count();
        long submittedCount = documentRepository.countByStatus(DocumentStatus.SUBMITTED);
        long approvedCount = documentRepository.countByStatus(DocumentStatus.APPROVED);
        long publishedCount = documentRepository.countByStatus(DocumentStatus.PUBLISHED);
        long revisionCount = documentRepository.countByStatus(DocumentStatus.REVISION_REQUIRED);
        long rejectedCount = documentRepository.countByStatus(DocumentStatus.REJECTED);

        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("documentCount", totalDocuments);
        model.addAttribute("pendingCount", submittedCount);
        model.addAttribute("publishedCount", publishedCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("revisionCount", revisionCount);
        model.addAttribute("rejectedCount", rejectedCount);
        model.addAttribute("publicationRate", percent(publishedCount, totalDocuments));
        model.addAttribute("reviewRate", percent(approvedCount + publishedCount + rejectedCount + revisionCount, totalDocuments));
        model.addAttribute("categoryCounts", documentRepository.countByCategory());
        model.addAttribute("facultyCounts", documentRepository.countByFaculty());
        model.addAttribute("recentDocuments", documentRepository.findTop8ByOrderByCreatedAtDesc());
        return "admin/dashboard";
    }

    private long percent(long value, long total) {
        return total == 0 ? 0 : Math.round(value * 100.0 / total);
    }
}
