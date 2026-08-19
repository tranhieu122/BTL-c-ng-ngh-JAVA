package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.ReviewAction;

import java.util.List;

public interface ReviewService {
    Document review(Long documentId, ReviewAction action, String comment, User reviewer);
    List<ApprovalHistory> history(Long documentId);
}
