package com.hieu.edurepo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DocumentReviewForm {
    @NotNull(message = "Vui lòng chọn số sao")
    @Min(value = 1, message = "Số sao phải từ 1 đến 5")
    @Max(value = 5, message = "Số sao phải từ 1 đến 5")
    private Integer rating;
    @Size(max = 500, message = "Nhận xét không được vượt quá 500 ký tự")
    private String comment;
    private boolean helpful;
    private boolean easyToUnderstand;
    private boolean onTopic;
    private boolean goodFileQuality;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isHelpful() { return helpful; }
    public void setHelpful(boolean helpful) { this.helpful = helpful; }
    public boolean isEasyToUnderstand() { return easyToUnderstand; }
    public void setEasyToUnderstand(boolean easyToUnderstand) { this.easyToUnderstand = easyToUnderstand; }
    public boolean isOnTopic() { return onTopic; }
    public void setOnTopic(boolean onTopic) { this.onTopic = onTopic; }
    public boolean isGoodFileQuality() { return goodFileQuality; }
    public void setGoodFileQuality(boolean goodFileQuality) { this.goodFileQuality = goodFileQuality; }
}
