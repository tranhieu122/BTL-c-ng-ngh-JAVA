package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.dto.AuditLogFilter;
import com.hieu.edurepo.entity.AuditLog;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.AuditLogRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AuditLogServiceImpl implements AuditLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogServiceImpl.class);
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(password|passwd|pwd|mật\\s*khẩu)\\s*[:=]\\s*[^,;\\s]+"
    );

    private final AuditLogRepository repository;
    private final ObjectProvider<HttpServletRequest> requestProvider;
    private final TransactionTemplate requiresNew;

    public AuditLogServiceImpl(AuditLogRepository repository,
                               ObjectProvider<HttpServletRequest> requestProvider,
                               PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requestProvider = requestProvider;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(AuditAction action, AuditTargetType targetType, Long targetId,
                       String description, AuditResult result) {
        record(SecurityContextHolder.getContext().getAuthentication(), action, targetType, targetId, description, result);
    }

    @Override
    public void record(AuditAction action, AuditTargetType targetType, Long targetId, String targetName,
                       String description, AuditResult result) {
        AuditLog log = baseLog(action, targetType, targetId, targetName, description, result);
        applyAuthentication(log, SecurityContextHolder.getContext().getAuthentication());
        persistSafely(log);
    }

    @Override
    public void record(Authentication authentication, AuditAction action, AuditTargetType targetType, Long targetId,
                       String description, AuditResult result) {
        AuditLog log = baseLog(action, targetType, targetId, null, description, result);
        applyAuthentication(log, authentication);
        persistSafely(log);
    }

    private void applyAuthentication(AuditLog log, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                log.setActorId(principal.getId());
                log.setActorName(normalize(principal.getFullName(), 255));
                log.setActorIdentifier(normalize(principal.getUsername(), 255));
            } else {
                log.setActorIdentifier(normalize(authentication.getName(), 255));
            }
            log.setActorRoles(authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                    .sorted().collect(Collectors.joining(", ")));
        } else {
            log.setActorName("Khách");
            log.setActorRoles("GUEST");
        }
    }

    @Override
    public void recordAsUser(User actor, AuditAction action, AuditTargetType targetType, Long targetId,
                             String description, AuditResult result) {
        AuditLog log = baseLog(action, targetType, targetId, null, description, result);
        applyActor(log, actor);
        persistSafely(log);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransactionalAsUser(User actor, AuditAction action, AuditTargetType targetType, Long targetId,
                                          String targetName, String description, AuditResult result) {
        AuditLog log = baseLog(action, targetType, targetId, targetName, description, result);
        applyActor(log, actor);
        // Không dùng REQUIRES_NEW ở đây: workflow, lịch sử duyệt và audit phải commit/rollback cùng nhau.
        repository.save(log);
    }

    @Override
    public void recordAnonymous(String identifier, AuditAction action, AuditTargetType targetType, Long targetId,
                                String description, AuditResult result) {
        AuditLog log = baseLog(action, targetType, targetId, null, description, result);
        log.setActorIdentifier(normalize(identifier, 255));
        log.setActorName("Khách");
        log.setActorRoles("GUEST");
        persistSafely(log);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLog> search(AuditLogFilter filter, Pageable pageable) {
        AuditLogFilter criteria = filter == null ? new AuditLogFilter() : filter;
        LocalDate from = criteria.getFromDate();
        LocalDate to = criteria.getToDate();
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        Specification<AuditLog> specification = Specification.unrestricted();
        if (criteria.getAction() != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("action"), criteria.getAction()));
        if (criteria.getActorId() != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("actorId"), criteria.getActorId()));
        String actorRole = normalize(criteria.getActorRole(), 50);
        if (actorRole != null) {
            String rolePattern = "%" + actorRole.toUpperCase(java.util.Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.like(cb.upper(root.get("actorRoles")), rolePattern));
        }
        if (criteria.getTargetType() != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("targetType"), criteria.getTargetType()));
        if (criteria.getResult() != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("result"), criteria.getResult()));
        if (from != null) {
            var start = from.atStartOfDay();
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), start));
        }
        if (to != null) {
            var endExclusive = to.plusDays(1).atStartOfDay();
            specification = specification.and((root, query, cb) -> cb.lessThan(root.get("occurredAt"), endExclusive));
        }
        String keyword = normalize(criteria.getKeyword(), 1000);
        if (keyword != null) {
            String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("description")), pattern));
        }
        return repository.findAll(specification, pageable);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public AuditLog findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi nhật kí"));
    }

    private AuditLog baseLog(AuditAction action, AuditTargetType targetType, Long targetId, String targetName,
                             String description, AuditResult result) {
        AuditLog log = new AuditLog();
        log.setAction(Objects.requireNonNull(action));
        log.setTargetType(Objects.requireNonNull(targetType));
        log.setTargetId(targetId);
        log.setTargetName(normalize(targetName, 255));
        log.setDescription(sanitize(description));
        log.setResult(result == null ? AuditResult.SUCCESS : result);
        try {
            HttpServletRequest request = requestProvider.getIfAvailable();
            if (request != null) {
                log.setIpAddress(normalize(request.getRemoteAddr(), 64));
                log.setUserAgent(normalize(request.getHeader("User-Agent"), 500));
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Không có request HTTP để ghi IP/User-Agent", exception);
        }
        return log;
    }

    private void persistSafely(AuditLog log) {
        try {
            requiresNew.executeWithoutResult(status -> repository.saveAndFlush(log));
        } catch (RuntimeException exception) {
            LOGGER.warn("Không thể ghi nhật kí hệ thống cho hành động {}", log.getAction(), exception);
        }
    }

    private void applyActor(AuditLog log, User actor) {
        if (actor == null) return;
        log.setActorId(actor.getId());
        log.setActorName(normalize(actor.getFullName(), 255));
        log.setActorIdentifier(normalize(actor.getEmail(), 255));
        if (actor.getRoles() != null) {
            log.setActorRoles(actor.getRoles().stream().map(role -> role.getName().name())
                    .sorted().collect(Collectors.joining(", ")));
        }
    }

    private String sanitize(String value) {
        String text = value == null || value.isBlank() ? "Không có mô tả" : value.trim();
        text = SENSITIVE_VALUE.matcher(text).replaceAll("$1=[ĐÃ ẨN]");
        return normalize(text.replaceAll("[\\r\\n\\t]+", " "), 1000);
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
