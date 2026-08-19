package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.ReviewAction;
import jakarta.validation.constraints.NotNull;

public class ReviewForm {
    @NotNull(message = "Vui lòng chọn quyết định")
    private ReviewAction action;
    private String comment;

    public ReviewAction getAction() { return action; }
    public void setAction(ReviewAction action) { this.action = action; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
