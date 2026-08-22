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

@Controller
@RequestMapping("/admin/users")
public class UserManagementController {

    private final UserService userService;
    private final RoleRepository roleRepository;

    public UserManagementController(UserService userService, RoleRepository roleRepository) {
        this.userService = userService;
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
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
                && form.getPassword().length() < 8) {
            bindingResult.rejectValue("password", "size", "Mật khẩu phải có ít nhất 8 ký tự");
        }
        if (bindingResult.hasErrors()) {
            return renderForm(model);
        }

        User user = creating ? new User() : userService.findById(form.getId());
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
            userService.save(user, changePassword);
        } catch (IllegalStateException exception) {
            if ("EMAIL_EXISTS".equals(exception.getMessage())) {
                bindingResult.rejectValue("email", "email.exists", "Email này đã được sử dụng");
                return renderForm(model);
            }
            throw exception;
        }
        redirectAttributes.addFlashAttribute("success", "Đã lưu người dùng");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        User user = userService.findById(id);
        if (isCurrentUser(user, authentication)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không thể tự xóa tài khoản đang đăng nhập");
            return "redirect:/admin/users";
        }
        try {
            userService.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Đã xóa người dùng");
        } catch (IllegalStateException exception) {
            if (!"USER_IN_USE".equals(exception.getMessage())) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("error",
                    "Không thể xóa người dùng đã có tài liệu hoặc lịch sử kiểm duyệt");
        } catch (DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "Không thể xóa người dùng đã có tài liệu hoặc lịch sử kiểm duyệt");
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
}
