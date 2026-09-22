package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.*;
import com.hieu.edurepo.enums.*;
import com.hieu.edurepo.repository.*;
import com.hieu.edurepo.security.CustomUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dịch vụ cung cấp ảnh chụp trạng thái tức thời (Snapshot) cho kết nối thời gian thực SSE.
 * <p>
 * Đồng bộ ngay lập tức các thay đổi về hồ sơ cá nhân, danh sách tài liệu sở hữu và hàng đợi kiểm duyệt.
 * Kiểm tra nghiêm ngặt tính hợp lệ của phiên đăng nhập (phát hiện tài khoản bị khóa hoặc đổi mật khẩu).
 * </p>
 */
@Service
public class RealtimeSnapshotService {

    private final UserRepository users;
    private final DocumentRepository documents;
    private final RealtimeChangeTracker changes;

    public RealtimeSnapshotService(UserRepository users, DocumentRepository documents,
                                   RealtimeChangeTracker changes) {
        this.users = users;
        this.documents = documents;
        this.changes = changes;
    }

    /**
     * Bản ghi tổng hợp ảnh chụp trạng thái thời gian thực của phiên làm việc.
     */
    public record Snapshot(ProfileView profile, List<LiveDocument> documents, List<LiveDocument> reviewQueue,
                           boolean canReview, RealtimeChangeTracker.Revision revision) { }

    /**
     * Tạo ảnh chụp trạng thái cho người dùng hiện tại đang kết nối kênh SSE.
     *
     * @param principal Thông tin tài khoản người dùng đăng nhập
     * @return Đối tượng Snapshot chứa dữ liệu hồ sơ, danh sách bài viết và số hiệu phiên bản
     * @throws AccessDeniedException Nếu phiên làm việc hết hạn hoặc tài khoản bị vô hiệu hóa
     */
    @Transactional(readOnly = true)
    public Snapshot snapshot(CustomUserPrincipal principal) {
        var account = users.findById(principal.getId()).orElseThrow(() -> new AccessDeniedException("Phiên đăng nhập đã hết hạn"));

        // Kiểm tra an toàn: tài khoản bị khóa, bị xóa mềm hoặc đã thay đổi mật khẩu/quyền hạn
        if (!account.isEnabled() || account.getDeletedAt() != null
                || !Objects.equals(account.getPassword(), principal.getPassword())
                || !Objects.equals(account.getEmail(), principal.getUsername())
                || !account.getRoles().stream().map(role -> "ROLE_" + role.getName()).collect(Collectors.toSet())
                    .equals(principal.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toSet())))
            throw new AccessDeniedException("Phiên làm việc đã hết hạn hoặc quyền hạn đã thay đổi");

        // Kiểm tra xem người dùng có quyền thẩm định học liệu hay không (Admin hoặc Reviewer)
        boolean reviewer = account.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN || role.getName() == RoleName.REVIEWER);

        return new Snapshot(ProfileView.from(account), documents.liveOwned(account.getId()),
                reviewer ? documents.liveQueue(List.of(DocumentStatus.SUBMITTED, DocumentStatus.RESUBMITTED,
                        DocumentStatus.UNDER_REVIEW, DocumentStatus.APPROVED)) : List.of(), reviewer,
                changes.current(account.getId(), reviewer));
    }
}
