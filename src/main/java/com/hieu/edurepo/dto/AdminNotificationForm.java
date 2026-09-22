package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.NotificationLevel;
import com.hieu.edurepo.enums.NotificationTargetType;
import com.hieu.edurepo.enums.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Biểu mẫu DTO dùng cho Quản trị viên soạn và phát hành thông báo hệ thống.
 */
public class AdminNotificationForm {
    @NotBlank(message = "Tiêu đề thông báo không được để trống")
    @Size(max = 180, message = "Tiêu đề không được vượt quá 180 ký tự")
    private String title;

    @NotBlank(message = "Nội dung thông báo không được để trống")
    @Size(max = 1000, message = "Nội dung không được vượt quá 1000 ký tự")
    private String content;

    @NotNull(message = "Vui lòng chọn đối tượng nhận")
    private NotificationTargetType targetType;

    private RoleName role;
    private Long receiverId;

    @NotNull(message = "Vui lòng chọn mức độ thông báo")
    private NotificationLevel level = NotificationLevel.NORMAL;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime sendAt;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(NotificationTargetType targetType) {
        this.targetType = targetType;
    }

    public RoleName getRole() {
        return role;
    }

    public void setRole(RoleName role) {
        this.role = role;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public NotificationLevel getLevel() {
        return level;
    }

    public void setLevel(NotificationLevel level) {
        this.level = level;
    }

    public LocalDateTime getSendAt() {
        return sendAt;
    }

    public void setSendAt(LocalDateTime sendAt) {
        this.sendAt = sendAt;
    }
}
