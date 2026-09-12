package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.AdminNotificationForm;
import com.hieu.edurepo.dto.NotificationPage;
import com.hieu.edurepo.dto.NotificationView;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Notification;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.NotificationLevel;
import com.hieu.edurepo.enums.NotificationType;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.NotificationRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public NotificationPage list(Long recipientId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        var result = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(
                recipientId, PageRequest.of(safePage, safeSize));
        return new NotificationPage(result.getContent().stream().map(NotificationView::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public NotificationView findForRecipient(Long recipientId, Long notificationId) {
        return notifications.findByIdAndRecipientId(notificationId, recipientId)
                .map(NotificationView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo."));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId) {
        return notifications.countByRecipientIdAndReadFalse(recipientId);
    }

    public void markRead(Long recipientId, Long notificationId) {
        notifications.markRead(notificationId, recipientId, now());
    }

    public void markAllRead(Long recipientId) {
        notifications.markAllRead(recipientId, now());
    }

    @Transactional(readOnly = true)
    public List<User> activeUsers() {
        return users.findByEnabledTrueAndDeletedAtIsNullOrderByFullNameAscEmailAsc();
    }

    public int sendFromAdmin(Long senderId, AdminNotificationForm form) {
        User sender = users.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người gửi."));
        List<User> receivers = resolveReceivers(form);
        if (receivers.isEmpty()) {
            throw new IllegalArgumentException("NO_RECEIVER");
        }

        String batchKey = "admin:" + senderId + ":" + UUID.randomUUID();
        LocalDateTime sendAt = form.getSendAt() == null ? now() : form.getSendAt();
        String title = trim(form.getTitle());
        String content = trim(form.getContent());
        NotificationLevel level = form.getLevel() == null ? NotificationLevel.NORMAL : form.getLevel();
        List<Notification> pending = new ArrayList<>();

        for (User receiver : receivers) {
            String eventKey = batchKey + ":" + receiver.getId();
            if (notifications.existsByEventKey(eventKey)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setSender(sender);
            notification.setReceiver(receiver);
            notification.setType(NotificationType.ADMIN_MESSAGE);
            notification.setLevel(level);
            notification.setEventKey(eventKey);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setSendAt(sendAt);
            pending.add(notification);
        }
        if (pending.isEmpty()) {
            return 0;
        }
        return notifications.saveAllAndFlush(pending).size();
    }

    public void submitted(Document document) {
        List<User> recipients = users.findActiveByRoles(Set.of(RoleName.REVIEWER, RoleName.ADMIN));
        String event = eventKey("submitted", document.getId());
        for (User recipient : recipients) {
            create(recipient, NotificationType.DOCUMENT_SUBMITTED, document, event + ":" + recipient.getId(),
                    "Tài liệu mới chờ duyệt", document.getTitle());
        }
    }

    public void reviewed(Document document, NotificationType type, String eventKey) {
        User author = document.getCreatedBy();
        if (author == null || author.getDeletedAt() != null) return;
        create(author, type, document, eventKey, title(type), document.getTitle());
    }

    private List<User> resolveReceivers(AdminNotificationForm form) {
        Map<Long, User> unique = new LinkedHashMap<>();
        List<User> candidates = switch (form.getTargetType()) {
            case ALL -> users.findByEnabledTrueAndDeletedAtIsNullOrderByFullNameAscEmailAsc();
            case ROLE -> form.getRole() == null ? List.of() : users.findActiveByRoles(Set.of(form.getRole()));
            case USER -> form.getReceiverId() == null
                    ? List.of()
                    : users.findById(form.getReceiverId())
                    .filter(user -> user.isEnabled() && user.getDeletedAt() == null)
                    .map(List::of)
                    .orElseGet(List::of);
        };
        for (User candidate : candidates) {
            if (candidate.getId() != null) {
                unique.putIfAbsent(candidate.getId(), candidate);
            }
        }
        return List.copyOf(unique.values());
    }

    private void create(User recipient, NotificationType type, Document document, String eventKey,
                        String title, String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setLevel(NotificationLevel.NORMAL);
        notification.setDocumentId(document.getId());
        notification.setEventKey(eventKey);
        notification.setTitle(title);
        notification.setMessage(message);
        notifications.save(notification);
    }

    private String eventKey(String action, Long documentId) {
        return action + ":" + documentId + ":" + UUID.randomUUID();
    }

    private String title(NotificationType type) {
        return switch (type) {
            case ADMIN_MESSAGE -> "Thông báo từ quản trị viên";
            case DOCUMENT_APPROVED -> "Tài liệu đã được phê duyệt";
            case DOCUMENT_REJECTED -> "Tài liệu bị từ chối";
            case DOCUMENT_REVISION_REQUIRED -> "Tài liệu cần chỉnh sửa";
            case DOCUMENT_PUBLISHED -> "Tài liệu đã được công bố";
            case DOCUMENT_SUBMITTED -> "Tài liệu mới chờ duyệt";
        };
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
