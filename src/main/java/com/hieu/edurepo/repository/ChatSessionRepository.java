package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.ChatSession;
import com.hieu.edurepo.enums.ChatSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy xuất dữ liệu bảng {@code chat_session}.
 * Đảm bảo Data Isolation: mọi truy vấn session của người dùng đều bắt buộc lọc theo userId.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /**
     * Lấy danh sách session của một user theo status với phân trang, sắp xếp theo thời gian hoạt động mới nhất.
     */
    Page<ChatSession> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, ChatSessionStatus status, Pageable pageable);

    /**
     * Lấy toàn bộ session ACTIVE của một user, sắp xếp mới nhất lên đầu.
     */
    List<ChatSession> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, ChatSessionStatus status);

    /**
     * Tìm session theo id và userId, loại trừ các session có trạng thái statusExcluded (thường là DELETED).
     */
    Optional<ChatSession> findByIdAndUserIdAndStatusNot(Long id, Long userId, ChatSessionStatus statusExcluded);

    /**
     * Tìm session chính xác theo id và userId (dùng cho authorization và kiểm tra IDOR).
     */
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    /**
     * Đếm số lượng session theo user và trạng thái.
     */
    long countByUserIdAndStatus(Long userId, ChatSessionStatus status);

    /**
     * Kiểm tra nhanh sự tồn tại của session thuộc sở hữu của user.
     */
    boolean existsByIdAndUserId(Long id, Long userId);
}
