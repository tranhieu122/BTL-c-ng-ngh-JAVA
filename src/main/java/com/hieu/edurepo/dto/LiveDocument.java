package com.hieu.edurepo.dto;
import com.hieu.edurepo.enums.DocumentStatus;
import java.time.LocalDateTime;
/**
 * DTO dữ liệu rút gọn của tài liệu phục vụ phát sóng cập nhật thời gian thực qua SSE.
 */
public record LiveDocument(Long id, String title, DocumentStatus status, LocalDateTime updatedAt) { }
