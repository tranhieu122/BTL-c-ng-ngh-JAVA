package com.hieu.edurepo.entity;

import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;
    private Long actorId;
    @Column(length = 255)
    private String actorName;
    @Column(length = 255)
    private String actorIdentifier;
    @Column(length = 255)
    private String actorRoles;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditTargetType targetType;
    private Long targetId;
    @Column(length = 255)
    private String targetName;
    @Column(nullable = false, length = 1000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditResult result;
    @Column(length = 64)
    private String ipAddress;
    @Column(length = 500)
    private String userAgent;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorIdentifier() { return actorIdentifier; }
    public void setActorIdentifier(String actorIdentifier) { this.actorIdentifier = actorIdentifier; }
    public String getActorRoles() { return actorRoles; }
    public void setActorRoles(String actorRoles) { this.actorRoles = actorRoles; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public AuditTargetType getTargetType() { return targetType; }
    public void setTargetType(AuditTargetType targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AuditResult getResult() { return result; }
    public void setResult(AuditResult result) { this.result = result; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
