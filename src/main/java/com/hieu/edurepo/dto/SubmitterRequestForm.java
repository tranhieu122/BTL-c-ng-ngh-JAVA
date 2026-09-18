package com.hieu.edurepo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SubmitterRequestForm {
    @NotBlank(message = "Vui lòng nêu lý do bạn cần quyền nộp tài liệu")
    @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
