package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.service.SampleDataService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/sample-data")
@PreAuthorize("hasRole('ADMIN')")
public class SampleDataAdminController {

    private final SampleDataService sampleDataService;

    public SampleDataAdminController(SampleDataService sampleDataService) {
        this.sampleDataService = sampleDataService;
    }

    @PostMapping("/seed")
    public String seedSampleData(RedirectAttributes redirectAttributes) {
        try {
            int count = sampleDataService.seedSampleDocuments(true);
            if (count > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã nạp thành công " + count + " tài liệu học thuật mẫu vào kho học liệu EduRepo!");
            } else {
                redirectAttributes.addFlashAttribute("infoMessage",
                        "Các tài liệu mẫu đã có sẵn trong kho học liệu.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Lỗi khi nạp tài liệu mẫu: " + e.getMessage());
        }
        return "redirect:/repository";
    }
}
