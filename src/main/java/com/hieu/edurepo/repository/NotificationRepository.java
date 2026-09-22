package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Kho lưu trữ thông báo hệ thống và quản lý trạng thái đã đọc của người dùng.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);
    long countByRecipientIdAndReadFalse(Long recipientId);
    boolean existsByEventKey(String eventKey);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :readAt where n.id = :id and n.recipient.id = :recipientId and n.read = false")
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :readAt where n.recipient.id = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("readAt") LocalDateTime readAt);
}
