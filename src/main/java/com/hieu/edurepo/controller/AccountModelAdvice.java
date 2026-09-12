package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.ProfileView;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AccountModelAdvice {
    private final UserRepository users;
    public AccountModelAdvice(UserRepository users) { this.users = users; }
    @ModelAttribute("currentAccount")
    public ProfileView currentAccount(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal)
            return users.findById(principal.getId()).map(ProfileView::from).orElse(null);
        return null;
    }
}
