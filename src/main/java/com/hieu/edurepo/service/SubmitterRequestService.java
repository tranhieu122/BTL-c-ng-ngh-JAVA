package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.SubmitterRequest;

import java.util.List;

public interface SubmitterRequestService {
    SubmitterRequest create(Long requesterId, String reason);
    List<SubmitterRequest> findForUser(Long requesterId);
    List<SubmitterRequest> findAll();
    boolean hasPending(Long requesterId);
    SubmitterRequest approve(Long requestId, Long adminId);
    SubmitterRequest reject(Long requestId, Long adminId, String rejectionReason);
}
