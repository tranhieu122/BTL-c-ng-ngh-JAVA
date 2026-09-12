package com.hieu.edurepo.security;

import com.hieu.edurepo.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Collection;

public class CustomUserPrincipal implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    private CustomUserPrincipal(User user, boolean accountNonLocked) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.enabled = user.isEnabled();
        this.accountNonLocked = accountNonLocked;
        this.authorities = user.getRoles() == null ? Collections.emptyList() : user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .toList();
    }

    public static CustomUserPrincipal from(User user) {
        return new CustomUserPrincipal(user, true);
    }

    public static CustomUserPrincipal from(User user, boolean accountNonLocked) {
        return new CustomUserPrincipal(user, accountNonLocked);
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }
}
