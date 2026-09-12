package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.AuditLogFilter;
import com.hieu.edurepo.entity.AuditLog;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface AuditLogService {
    void record(AuditAction action, AuditTargetType targetType, Long targetId,
                String description, AuditResult result);
    void record(AuditAction action, AuditTargetType targetType, Long targetId, String targetName,
                String description, AuditResult result);
    void record(Authentication authentication, AuditAction action, AuditTargetType targetType, Long targetId,
                String description, AuditResult result);
    void recordAsUser(User actor, AuditAction action, AuditTargetType targetType, Long targetId,
                      String description, AuditResult result);
    void recordAnonymous(String identifier, AuditAction action, AuditTargetType targetType, Long targetId,
                         String description, AuditResult result);
    Page<AuditLog> search(AuditLogFilter filter, Pageable pageable);
    AuditLog findById(Long id);
}
