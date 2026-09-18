package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.SubmitterRequest;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.enums.SubmitterRequestStatus;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.SubmitterRequestRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.SubmitterRequestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class SubmitterRequestServiceImpl implements SubmitterRequestService {
    private final SubmitterRequestRepository requests;
    private final UserRepository users;
    private final RoleRepository roles;

    public SubmitterRequestServiceImpl(SubmitterRequestRepository requests, UserRepository users,
                                       RoleRepository roles) {
        this.requests = requests;
        this.users = users;
        this.roles = roles;
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public SubmitterRequest create(Long requesterId, String reason) {
        User requester = users.lockById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        if (hasRole(requester, RoleName.SUBMITTER)) {
            throw new InvalidStatusException("Tài khoản của bạn đã có quyền nộp tài liệu.");
        }
        if (requests.existsByRequesterIdAndStatus(requesterId, SubmitterRequestStatus.PENDING)) {
            throw new InvalidStatusException("Bạn đang có một yêu cầu chờ xử lý.");
        }
        String normalizedReason = normalizeRequired(reason, "Vui lòng nhập lý do yêu cầu");
        if (normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("Lý do không được vượt quá 1000 ký tự");
        }
        SubmitterRequest request = new SubmitterRequest();
        request.setRequester(requester);
        request.setReason(normalizedReason);
        return requests.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmitterRequest> findForUser(Long requesterId) {
        return requests.findByRequesterIdOrderByCreatedAtDesc(requesterId);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<SubmitterRequest> findAll() {
        return requests.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPending(Long requesterId) {
        return requests.existsByRequesterIdAndStatus(requesterId, SubmitterRequestStatus.PENDING);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public SubmitterRequest approve(Long requestId, Long adminId) {
        SubmitterRequest request = requirePending(requestId);
        User requester = users.lockById(request.getRequester().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người gửi yêu cầu"));
        Role submitterRole = roles.findByName(RoleName.SUBMITTER)
                .orElseThrow(() -> new IllegalStateException("Thiếu vai trò SUBMITTER"));
        requester.getRoles().add(submitterRole);
        users.save(requester);
        complete(request, adminId, SubmitterRequestStatus.APPROVED, null);
        return requests.save(request);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public SubmitterRequest reject(Long requestId, Long adminId, String rejectionReason) {
        String normalizedReason = normalizeRequired(rejectionReason, "Bắt buộc nhập lý do từ chối");
        if (normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("Lý do từ chối không được vượt quá 1000 ký tự");
        }
        SubmitterRequest request = requirePending(requestId);
        complete(request, adminId, SubmitterRequestStatus.REJECTED, normalizedReason);
        return requests.save(request);
    }

    private SubmitterRequest requirePending(Long requestId) {
        SubmitterRequest request = requests.lockById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu"));
        if (request.getStatus() != SubmitterRequestStatus.PENDING) {
            throw new InvalidStatusException("Yêu cầu này đã được xử lý.");
        }
        return request;
    }

    private void complete(SubmitterRequest request, Long adminId, SubmitterRequestStatus status,
                          String rejectionReason) {
        User admin = users.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quản trị viên"));
        if (!hasRole(admin, RoleName.ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ ADMIN được xử lý yêu cầu");
        }
        request.setStatus(status);
        request.setRejectionReason(rejectionReason);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
    }

    private boolean hasRole(User user, RoleName role) {
        return user.getRoles().stream().anyMatch(item -> item.getName() == role);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
