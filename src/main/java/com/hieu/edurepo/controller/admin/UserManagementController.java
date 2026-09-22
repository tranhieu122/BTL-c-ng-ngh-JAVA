package com.hieu.edurepo.controller.admin;

import com.hieu.edurepo.dto.UserForm;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bộ điều hướng quản trị Danh sách thành viên người dùng (User Management Controller).
 * Tìm kiếm thành viên, phân bổ vai trò (Admin/Reviewer/User) và khóa/mở khóa tài khoản vi phạm.
 */
@Controller
@RequestMapping("/admin/users")
public class UserManagementController {

    private final UserService userService;
    private final RoleRepository roleRepository;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    public UserManagementController(UserService userService, RoleRepository roleRepository,
                                    com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.auditLogs = auditLogs;
    }

    @GetMapping
    public String list(Model model) {
        var users = userService.findAll();
        model.addAttribute("users", users);
        model.addAttribute("resetRequests", users.stream().filter(user -> user.getPasswordResetRequestedAt() != null).toList());
        auditLogs.record(com.hieu.edurepo.enums.AuditAction.USER_MANAGEMENT_VIEWED,
                com.hieu.edurepo.enums.AuditTargetType.PAGE, null, "Quản lý người dùng",
                "Xem danh sách quản lý người dùng", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        return "admin/users";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("userForm", new UserForm());
        model.addAttribute("roleNames", RoleName.values());
        return "admin/user-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id);
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setFullName(user.getFullName());
        form.setEmail(user.getEmail());
        form.setEnabled(user.isEnabled());
        form.setRoles((user.getRoles() == null ? Collections.<Role>emptySet() : user.getRoles())
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet()));
        model.addAttribute("userForm", form);
        model.addAttribute("roleNames", RoleName.values());
        return "admin/user-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("userForm") UserForm form, BindingResult bindingResult,
            Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        boolean creating = form.getId() == null;
        if (creating && (form.getPassword() == null || form.getPassword().isBlank())) {
            bindingResult.rejectValue("password", "required", "Mật khẩu không được để trống");
        }
        if (form.getPassword() != null && !form.getPassword().isBlank()
                && !com.hieu.edurepo.util.PasswordPolicy.isValid(form.getPassword())) {
            bindingResult.rejectValue("password", "size", com.hieu.edurepo.util.PasswordPolicy.MESSAGE);
        }
        if (bindingResult.hasErrors()) {
            return renderForm(model);
        }

        User user = creating ? new User() : userService.findById(form.getId());
        boolean previousEnabled = user.isEnabled();
        Set<RoleName> previousRoles = user.getRoles() == null ? Set.of() : user.getRoles().stream()
                .map(Role::getName).collect(Collectors.toSet());
        Set<RoleName> roleNames = resolveRoleNames(form);
        if (!creating && isCurrentUser(user, authentication)
                && (!form.isEnabled() || !roleNames.contains(RoleName.ADMIN))) {
            bindingResult.reject("self.lockout", "Bạn không thể tự khóa tài khoản hoặc tự bỏ quyền ADMIN");
            return renderForm(model);
        }

        user.setFullName(form.getFullName());
        user.setEmail(form.getEmail());
        user.setEnabled(form.isEnabled());
        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new IllegalStateException("Thiếu vai trò: " + name)))
                .collect(Collectors.toSet());
        user.setRoles(roles);

        boolean changePassword = form.getPassword() != null && !form.getPassword().isBlank();
        if (changePassword) {
            user.setPassword(form.getPassword());
        }
        try {
            user = userService.save(user, changePassword);
        } catch (IllegalStateException exception) {
            if ("EMAIL_EXISTS".equals(exception.getMessage())) {
                bindingResult.rejectValue("email", "email.exists", "Email này đã được sử dụng");
                return renderForm(model);
            }
            throw exception;
        }
        audit(creating ? com.hieu.edurepo.enums.AuditAction.USER_CREATED
                        : com.hieu.edurepo.enums.AuditAction.USER_UPDATED,
                user.getId(), (creating ? "Tạo người dùng: " : "Cập nhật người dùng: ") + user.getEmail(),
                com.hieu.edurepo.enums.AuditResult.SUCCESS);
        if (!creating && !previousRoles.equals(roleNames)) {
            audit(com.hieu.edurepo.enums.AuditAction.USER_ROLE_CHANGED, user.getId(),
                    "Thay đổi vai trò tài khoản " + user.getEmail() + " từ " + previousRoles + " thành " + roleNames,
                    com.hieu.edurepo.enums.AuditResult.SUCCESS);
        }
        if (!creating && previousEnabled != user.isEnabled()) {
            audit(user.isEnabled() ? com.hieu.edurepo.enums.AuditAction.USER_UNLOCKED
                            : com.hieu.edurepo.enums.AuditAction.USER_LOCKED, user.getId(),
                    "Thay đổi trạng thái tài khoản " + user.getEmail() + " thành "
                            + (user.isEnabled() ? "đang hoạt động" : "đã khóa"),
                    com.hieu.edurepo.enums.AuditResult.SUCCESS);
        }
        redirectAttributes.addFlashAttribute("success", "Đã lưu người dùng");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User user = userService.findById(id);
        if (isCurrentUser(user, authentication)) {
            audit(com.hieu.edurepo.enums.AuditAction.USER_DELETED, id,
                    "Từ chối tự xóa tài khoản đang đăng nhập", com.hieu.edurepo.enums.AuditResult.FAILURE);
            redirectAttributes.addFlashAttribute("error", "Bạn không thể tự xóa tài khoản đang đăng nhập");
            return "redirect:/admin/users";
        }
        try {
            userService.deleteById(id);
            audit(com.hieu.edurepo.enums.AuditAction.USER_DELETED, id,
                    "Xóa người dùng: " + user.getEmail(), com.hieu.edurepo.enums.AuditResult.SUCCESS);
            redirectAttributes.addFlashAttribute("success", "Đã xóa người dùng");
        } catch (IllegalStateException exception) {
            if (!"USER_IN_USE".equals(exception.getMessage())) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("error",
                    "Không thể xóa người dùng đã có tài liệu hoặc lịch sử kiểm duyệt");
            audit(com.hieu.edurepo.enums.AuditAction.USER_DELETED, id,
                    "Không thể xóa người dùng đang được tham chiếu: " + user.getEmail(),
                    com.hieu.edurepo.enums.AuditResult.FAILURE);
        } catch (DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "Không thể xóa người dùng đã có tài liệu hoặc lịch sử kiểm duyệt");
            audit(com.hieu.edurepo.enums.AuditAction.USER_DELETED, id,
                    "Không thể xóa người dùng đang được tham chiếu: " + user.getEmail(),
                    com.hieu.edurepo.enums.AuditResult.FAILURE);
        }
        return "redirect:/admin/users";
    }

    private Set<RoleName> resolveRoleNames(UserForm form) {
        return form.getRoles() == null ? Collections.emptySet() : form.getRoles();
    }

    private boolean isCurrentUser(User user, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            return user.getId() != null && user.getId().equals(principal.getId());
        }
        return user.getEmail() != null && user.getEmail().equalsIgnoreCase(authentication.getName());
    }

    private String renderForm(Model model) {
        model.addAttribute("roleNames", RoleName.values());
        return "admin/user-form";
    }

    private void audit(com.hieu.edurepo.enums.AuditAction action, Long targetId, String description,
                       com.hieu.edurepo.enums.AuditResult result) {
        String targetName = description == null || !description.contains(": ")
                ? null : description.substring(description.indexOf(": ") + 2);
        auditLogs.record(action, com.hieu.edurepo.enums.AuditTargetType.USER, targetId, targetName, description, result);
    }
}
