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
        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("documentCount", documentRepository.count());
        model.addAttribute("pendingCount", documentRepository.countByStatus(DocumentStatus.SUBMITTED));
        model.addAttribute("publishedCount", documentRepository.countByStatus(DocumentStatus.PUBLISHED));
        return "admin/dashboard";
    }
}
