package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.hieu.edurepo.enums.AuditAction;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    Optional<AuditLog> findTopByActionOrderByOccurredAtDescIdDesc(AuditAction action);
}
