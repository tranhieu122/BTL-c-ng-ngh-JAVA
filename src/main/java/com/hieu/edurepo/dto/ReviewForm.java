package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.ReviewAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class ReviewForm {
    @NotNull(message = "Vui lòng chọn quyết định")
    private ReviewAction action;
    @Size(max = 5000, message = "Góp ý không được vượt quá 5000 ký tự")
    private String comment;

    @Min(value = 1, message = "Điểm chất lượng nội dung phải từ 1 đến 5")
    @Max(value = 5, message = "Điểm chất lượng nội dung phải từ 1 đến 5")
    private Integer contentQualityScore;

    @Min(value = 1, message = "Điểm hiệu quả giảng dạy phải từ 1 đến 5")
    @Max(value = 5, message = "Điểm hiệu quả giảng dạy phải từ 1 đến 5")
    private Integer teachingEffectivenessScore;

    @Min(value = 1, message = "Điểm dễ sử dụng phải từ 1 đến 5")
    @Max(value = 5, message = "Điểm dễ sử dụng phải từ 1 đến 5")
    private Integer easeOfUseScore;

    public ReviewAction getAction() { return action; }
    public void setAction(ReviewAction action) { this.action = action; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Integer getContentQualityScore() { return contentQualityScore; }
    public void setContentQualityScore(Integer contentQualityScore) { this.contentQualityScore = contentQualityScore; }
    public Integer getTeachingEffectivenessScore() { return teachingEffectivenessScore; }
    public void setTeachingEffectivenessScore(Integer teachingEffectivenessScore) { this.teachingEffectivenessScore = teachingEffectivenessScore; }
    public Integer getEaseOfUseScore() { return easeOfUseScore; }
    public void setEaseOfUseScore(Integer easeOfUseScore) { this.easeOfUseScore = easeOfUseScore; }
}
