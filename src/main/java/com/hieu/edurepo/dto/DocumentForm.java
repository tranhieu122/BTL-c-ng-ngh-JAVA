package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.LicenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class DocumentForm {
    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    @NotBlank(message = "Mô tả không được để trống")
    @Size(max = 10000, message = "Mô tả không được vượt quá 10000 ký tự")
    private String description;
    @Size(max = 255, message = "Tên tác giả không được vượt quá 255 ký tự")
    private String authorName;

    @Size(max = 2000, message = "Tóm tắt không được vượt quá 2000 ký tự")
    private String summary;

    @Size(max = 1000, message = "Từ khóa không được vượt quá 1000 ký tự")
    private String keywords;

    @Size(max = 10, message = "Mã ngôn ngữ không hợp lệ")
    private String languageCode = "vi";

    @NotNull(message = "Vui lòng chọn loại học liệu")
    private LearningResourceType learningResourceType = LearningResourceType.OTHER;

    @NotNull(message = "Vui lòng chọn trình độ")
    private EducationLevel educationLevel = EducationLevel.ALL_LEVELS;

    private LicenseType licenseType;

    @NotNull(message = "Vui lòng chọn danh mục")
    private Long categoryId;

    @NotNull(message = "Vui lòng chọn khoa")
    private Long facultyId;

    @NotNull(message = "Vui lòng chọn bộ môn")
    private Long departmentId;
    private MultipartFile file;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public LearningResourceType getLearningResourceType() { return learningResourceType; }
    public void setLearningResourceType(LearningResourceType learningResourceType) { this.learningResourceType = learningResourceType; }
    public EducationLevel getEducationLevel() { return educationLevel; }
    public void setEducationLevel(EducationLevel educationLevel) { this.educationLevel = educationLevel; }
    public LicenseType getLicenseType() { return licenseType; }
    public void setLicenseType(LicenseType licenseType) { this.licenseType = licenseType; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
}
