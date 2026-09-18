package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.ProfileForm;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.ProfileService;
import com.hieu.edurepo.service.AvatarStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {
    private final ProfileService profiles;
    private final AvatarStorageService avatars;
    public ProfileController(ProfileService profiles, AvatarStorageService avatars) { this.profiles = profiles; this.avatars = avatars; }
    @InitBinder("profileForm")
    void allowedFields(WebDataBinder binder) { binder.setAllowedFields("fullName", "phoneNumber", "affiliation", "bio"); }
    @GetMapping
    String profile(@AuthenticationPrincipal CustomUserPrincipal principal, Model model) {
        var account = profiles.get(principal.getId());
        model.addAttribute("account", account);
        model.addAttribute("profileForm", account.form());
        return "profile/index";
    }
    @PostMapping
    String save(@AuthenticationPrincipal CustomUserPrincipal principal, @Valid @ModelAttribute ProfileForm profileForm,
                BindingResult binding, Model model, RedirectAttributes flash, HttpServletResponse response) {
        if (binding.hasErrors()) {
            model.addAttribute("account", profiles.get(principal.getId()));
            response.setStatus(400); return "profile/index";
        }
        profiles.update(principal.getId(), profileForm);
        flash.addFlashAttribute("success", "Đã lưu thông tin cá nhân.");
        return "redirect:/profile";
    }
    @GetMapping("/avatar")
    ResponseEntity<Resource> avatar(@AuthenticationPrincipal CustomUserPrincipal principal) {
        var profile = profiles.get(principal.getId());
        Resource resource = avatars.load(principal.getId(), profile.avatarRevision());
        String filename = resource.getFilename();
        var type = filename != null && filename.endsWith(".svg")
                ? MediaType.valueOf("image/svg+xml")
                : MediaType.IMAGE_PNG;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(resource);
    }
    @PostMapping("/avatar")
    String upload(@AuthenticationPrincipal CustomUserPrincipal principal, @RequestParam("avatar") MultipartFile file, RedirectAttributes flash) {
        profiles.uploadAvatar(principal.getId(), file);
        flash.addFlashAttribute("success", "Đã cập nhật ảnh đại diện."); return "redirect:/profile";
    }
    @PostMapping("/avatar/delete")
    String remove(@AuthenticationPrincipal CustomUserPrincipal principal, RedirectAttributes flash) {
        profiles.removeAvatar(principal.getId());
        flash.addFlashAttribute("success", "Đã khôi phục ảnh mặc định."); return "redirect:/profile";
    }
    @PostMapping("/password")
    String password(@AuthenticationPrincipal CustomUserPrincipal principal,
                    @RequestParam String currentPassword, @RequestParam String newPassword, @RequestParam String confirmPassword,
                    Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        profiles.changePassword(principal.getId(), currentPassword, newPassword, confirmPassword);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?passwordChanged";
    }
    @PostMapping("/delete")
    String delete(@AuthenticationPrincipal CustomUserPrincipal principal, @RequestParam String currentPassword,
                  @RequestParam String confirmation, @RequestParam(defaultValue = "false") boolean acknowledged,
                  Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        profiles.deleteAccount(principal.getId(), currentPassword, confirmation, acknowledged);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?accountDeleted";
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    String invalid(Exception exception, RedirectAttributes flash) {
        flash.addFlashAttribute("error", exception instanceof IllegalArgumentException ? exception.getMessage() : "Vui lòng nhập đầy đủ thông tin.");
        return "redirect:/profile";
    }
}
