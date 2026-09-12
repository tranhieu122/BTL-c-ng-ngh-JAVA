package com.hieu.edurepo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private boolean enabled = true;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private LocalDateTime loginLockedUntil;

    @Column(length = 30)
    private String phoneNumber;
    @Column(length = 150)
    private String affiliation;
    @Column(length = 1000)
    private String bio;
    private String avatarKey;
    @Column(length = 64)
    private String bootstrapKey;
    private java.time.LocalDateTime deletedAt;

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String value) { phoneNumber = value; }
    public String getAffiliation() { return affiliation; }
    public void setAffiliation(String value) { affiliation = value; }
    public String getBio() { return bio; }
    public void setBio(String value) { bio = value; }
    public String getAvatarKey() { return avatarKey; }
    public String getBootstrapKey() { return bootstrapKey; }
    public void setBootstrapKey(String value) { bootstrapKey = value; }
    public void setAvatarKey(String value) { avatarKey = value; }
    public java.time.LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(java.time.LocalDateTime value) { deletedAt = value; }

    private java.time.LocalDateTime passwordResetRequestedAt;

    public java.time.LocalDateTime getPasswordResetRequestedAt() { return passwordResetRequestedAt; }
    public void setPasswordResetRequestedAt(java.time.LocalDateTime value) { passwordResetRequestedAt = value; }

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLoginLockedUntil() {
        return loginLockedUntil;
    }

    public void setLoginLockedUntil(LocalDateTime loginLockedUntil) {
        this.loginLockedUntil = loginLockedUntil;
    }

    public boolean isLoginLockedAt(LocalDateTime time) {
        return loginLockedUntil != null && loginLockedUntil.isAfter(time);
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
    }
}
