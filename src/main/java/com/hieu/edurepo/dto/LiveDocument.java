package com.hieu.edurepo.dto;
import com.hieu.edurepo.enums.DocumentStatus;
import java.time.LocalDateTime;
public record LiveDocument(Long id, String title, DocumentStatus status, LocalDateTime updatedAt) { }
