package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.DocumentReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DocumentReportForm {
    @NotNull(message = "Vui lòng chọn lý do báo cáo")
    private DocumentReportReason reason;
    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    public DocumentReportReason getReason() { return reason; }
    public void setReason(DocumentReportReason reason) { this.reason = reason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
