package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    java.util.List<User> findByDeletedAtIsNull();
    java.util.List<User> findByEnabledTrueAndDeletedAtIsNullOrderByFullNameAscEmailAsc();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select count(u) from User u join u.roles r where r.name = :role and u.enabled = true and u.deletedAt is null")
    long countActiveRole(@org.springframework.data.repository.query.Param("role") com.hieu.edurepo.enums.RoleName role);
    @org.springframework.data.jpa.repository.Query("select distinct u from User u join u.roles r "
            + "where r.name in :roles and u.enabled = true and u.deletedAt is null")
    java.util.List<User> findActiveByRoles(@org.springframework.data.repository.query.Param("roles")
            java.util.Set<com.hieu.edurepo.enums.RoleName> roles);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> lockByEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update User u set u.passwordResetRequestedAt = :requestedAt "
            + "where lower(u.email) = :email and u.enabled = true and u.passwordResetRequestedAt is null")
    int requestPasswordReset(@org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("requestedAt") java.time.LocalDateTime requestedAt);
}
