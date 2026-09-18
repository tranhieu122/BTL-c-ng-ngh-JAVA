package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.ReviewAction;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    Document review(Long documentId, ReviewAction action, String comment, User reviewer);
    Document review(Long documentId, ReviewAction action, String comment, User reviewer,
                    Integer contentQualityScore, Integer teachingEffectivenessScore, Integer easeOfUseScore);
    List<ApprovalHistory> history(Long documentId);
    Page<ApprovalHistory> history(Long documentId, Pageable pageable);
}
