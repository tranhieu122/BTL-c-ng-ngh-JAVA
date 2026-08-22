package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.entity.Department;
import com.hieu.edurepo.entity.Faculty;
import com.hieu.edurepo.repository.DepartmentRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.FacultyRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/organization")
public class OrganizationManagementController {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final DocumentRepository documentRepository;

    public OrganizationManagementController(FacultyRepository facultyRepository,
                                            DepartmentRepository departmentRepository,
                                            DocumentRepository documentRepository) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(required = false) Boolean active, Model model) {
        addData(model);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedActive", active);
        model.addAttribute("faculty", new Faculty());
        model.addAttribute("department", new Department());
        return "admin/organization";
    }

    @PostMapping("/faculties")
    public String createFaculty(@Valid @ModelAttribute("faculty") Faculty faculty, BindingResult result,
                                Model model, RedirectAttributes redirectAttributes) {
        normalizeFaculty(faculty);
        if (faculty.getName() != null && facultyRepository.existsByNameIgnoreCase(faculty.getName())) {
            result.rejectValue("name", "exists", "Tên khoa đã tồn tại");
        }
        if (result.hasErrors()) {
            addData(model);
            model.addAttribute("department", new Department());
            return "admin/organization";
        }
        facultyRepository.save(faculty);
        redirectAttributes.addFlashAttribute("success", "Đã thêm khoa");
        return "redirect:/admin/organization";
    }

    @PostMapping("/departments")
    public String createDepartment(@Valid @ModelAttribute("department") Department department, BindingResult result,
                                   Model model, RedirectAttributes redirectAttributes) {
        if (department.getFaculty() == null || department.getFaculty().getId() == null) {
            result.rejectValue("faculty", "required", "Vui lòng chọn khoa");
        } else {
            Faculty faculty = facultyRepository.findById(department.getFaculty().getId()).orElse(null);
            if (faculty == null || !faculty.isActive()) {
                result.rejectValue("faculty", "invalid", "Khoa không hợp lệ hoặc đã ngừng sử dụng");
            } else {
                department.setFaculty(faculty);
            }
        }
        normalizeDepartment(department);
        if (department.getFaculty() != null && department.getFaculty().getId() != null
                && department.getName() != null
                && departmentRepository.existsByNameIgnoreCase(department.getName())) {
            result.rejectValue("name", "exists", "Tên bộ môn đã tồn tại trong hệ thống");
        }
        if (result.hasErrors()) {
            addData(model);
            model.addAttribute("faculty", new Faculty());
            return "admin/organization";
        }
        departmentRepository.save(department);
        redirectAttributes.addFlashAttribute("success", "Đã thêm bộ môn");
        return "redirect:/admin/organization";
    }

    @PostMapping("/faculties/{id}/disable")
    public String disableFaculty(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Faculty faculty = facultyRepository.findById(id).orElseThrow();
        faculty.setActive(!faculty.isActive());
        facultyRepository.save(faculty);
        redirectAttributes.addFlashAttribute("success", faculty.isActive() ? "Đã kích hoạt khoa" : "Đã ngừng sử dụng khoa");
        return "redirect:/admin/organization";
    }

    @PostMapping("/departments/{id}/disable")
    public String disableDepartment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Department department = departmentRepository.findById(id).orElseThrow();
        department.setActive(!department.isActive());
        departmentRepository.save(department);
        redirectAttributes.addFlashAttribute("success", department.isActive() ? "Đã kích hoạt bộ môn" : "Đã ngừng sử dụng bộ môn");
        return "redirect:/admin/organization";
    }

    @GetMapping("/faculties/{id}/edit")
    public String editFaculty(@PathVariable Long id, Model model) {
        model.addAttribute("faculty", facultyRepository.findById(id).orElseThrow());
        model.addAttribute("departmentCount", departmentRepository.countByFacultyId(id));
        model.addAttribute("documentCount", documentRepository.countByDepartmentFacultyId(id));
        model.addAttribute("editingFaculty", true);
        return "admin/organization-form";
    }

    @PostMapping("/faculties/{id}")
    public String updateFaculty(@PathVariable Long id, @Valid @ModelAttribute("faculty") Faculty changes,
                                BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        Faculty faculty = facultyRepository.findById(id).orElseThrow();
        normalizeFaculty(changes);
        if (changes.getName() != null && facultyRepository.existsByNameIgnoreCaseAndIdNot(changes.getName(), id)) {
            result.rejectValue("name", "exists", "Tên khoa đã tồn tại");
        }
        if (result.hasErrors()) {
            model.addAttribute("departmentCount", departmentRepository.countByFacultyId(id));
            model.addAttribute("documentCount", documentRepository.countByDepartmentFacultyId(id));
            model.addAttribute("editingFaculty", true);
            return "admin/organization-form";
        }
        faculty.setName(changes.getName());
        faculty.setDescription(changes.getDescription());
        facultyRepository.save(faculty);
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật khoa");
        return "redirect:/admin/organization";
    }

    @GetMapping("/departments/{id}/edit")
    public String editDepartment(@PathVariable Long id, Model model) {
        Department department = departmentRepository.findById(id).orElseThrow();
        model.addAttribute("department", department);
        model.addAttribute("activeFaculties", facultyRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("documentCount", documentRepository.countByDepartmentId(id));
        model.addAttribute("editingDepartment", true);
        return "admin/organization-form";
    }

    @PostMapping("/departments/{id}")
    public String updateDepartment(@PathVariable Long id, @Valid @ModelAttribute("department") Department changes,
                                   BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        Department department = departmentRepository.findById(id).orElseThrow();
        Faculty faculty = resolveFaculty(changes, result);
        normalizeDepartment(changes);
        if (faculty != null && changes.getName() != null
                && departmentRepository.existsByNameIgnoreCaseAndIdNot(changes.getName(), id)) {
            result.rejectValue("name", "exists", "Tên bộ môn đã tồn tại trong hệ thống");
        }
        if (result.hasErrors()) {
            model.addAttribute("activeFaculties", facultyRepository.findByActiveTrueOrderByNameAsc());
            model.addAttribute("documentCount", documentRepository.countByDepartmentId(id));
            model.addAttribute("editingDepartment", true);
            return "admin/organization-form";
        }
        department.setName(changes.getName());
        department.setDescription(changes.getDescription());
        department.setFaculty(faculty);
        departmentRepository.save(department);
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật bộ môn");
        return "redirect:/admin/organization";
    }

    @GetMapping("/api/faculties/{id}/departments")
    @ResponseBody
    public java.util.List<DepartmentOption> departmentsByFaculty(@PathVariable Long id) {
        return departmentRepository.findByFacultyIdAndActiveTrueOrderByNameAsc(id).stream()
                .map(item -> new DepartmentOption(item.getId(), item.getName()))
                .toList();
    }

    private Faculty resolveFaculty(Department department, BindingResult result) {
        if (department.getFaculty() == null || department.getFaculty().getId() == null) {
            result.rejectValue("faculty", "required", "Vui lòng chọn khoa");
            return null;
        }
        Faculty faculty = facultyRepository.findById(department.getFaculty().getId()).orElse(null);
        if (faculty == null || !faculty.isActive()) {
            result.rejectValue("faculty", "invalid", "Khoa không hợp lệ hoặc đã ngừng sử dụng");
        }
        return faculty;
    }

    private void normalizeFaculty(Faculty faculty) {
        if (faculty.getName() != null) faculty.setName(faculty.getName().trim());
        if (faculty.getDescription() != null) faculty.setDescription(faculty.getDescription().trim());
    }

    private void normalizeDepartment(Department department) {
        if (department.getName() != null) department.setName(department.getName().trim());
        if (department.getDescription() != null) department.setDescription(department.getDescription().trim());
    }

    private void addData(Model model) {
        model.addAttribute("faculties", facultyRepository.findAll());
        model.addAttribute("activeFaculties", facultyRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("departments", departmentRepository.findAll());
    }

    public record DepartmentOption(Long id, String name) { }
}
