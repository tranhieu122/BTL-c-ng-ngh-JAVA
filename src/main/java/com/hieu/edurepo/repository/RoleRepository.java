package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Kho lưu trữ thông tin các vai trò (Roles) và quyền hạn tài khoản trong hệ thống.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Role r where r.name = :name")
    Optional<Role> lockByName(@org.springframework.data.repository.query.Param("name") RoleName name);
    Optional<Role> findByName(RoleName name);
}
