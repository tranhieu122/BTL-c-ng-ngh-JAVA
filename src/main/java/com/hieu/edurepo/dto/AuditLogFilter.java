package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class AuditLogFilter {
    private AuditAction action;
    private Long actorId;
    private String actorRole;
    private AuditTargetType targetType;
    private AuditResult result;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
    private String keyword;

    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public AuditTargetType getTargetType() { return targetType; }
    public void setTargetType(AuditTargetType targetType) { this.targetType = targetType; }
    public AuditResult getResult() { return result; }
    public void setResult(AuditResult result) { this.result = result; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
