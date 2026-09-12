package com.hieu.edurepo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CollectionForm {
    @NotBlank(message = "Tên bộ sưu tập không được để trống")
    @Size(max = 150, message = "Tên bộ sưu tập không được vượt quá 150 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
