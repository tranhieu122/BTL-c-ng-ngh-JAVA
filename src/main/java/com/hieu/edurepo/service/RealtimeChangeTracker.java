package com.hieu.edurepo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bộ theo dõi thay đổi dữ liệu thời gian thực trong bộ nhớ (In-memory Change Signal Tracker).
 * <p>
 * Giúp các kết nối Server-Sent Events (SSE) phát hiện sự kiện thay đổi dữ liệu tức thì mà KHÔNG cần
 * liên tục thăm dò (polling) cơ sở dữ liệu khi không có biến động, tối ưu hiệu năng tuyệt đối cho máy chủ.
 * </p>
 */
@Service
public class RealtimeChangeTracker {

    /** Số hiệu phiên bản hàng đợi kiểm duyệt (tăng khi có bài nộp mới hoặc duyệt bài) */
    private final AtomicLong reviewRevision = new AtomicLong();

    /** Bản đồ lưu số hiệu phiên bản dữ liệu của từng người dùng cụ thể */
    private final ConcurrentHashMap<Long, AtomicLong> userRevisions = new ConcurrentHashMap<>();

    /**
     * Lấy số hiệu phiên bản hiện tại áp dụng cho một tài khoản người dùng.
     *
     * @param userId Mã người dùng
     * @param canReview Quyền kiểm duyệt của tài khoản (Reviewer/Admin)
     * @return Đối tượng Revision chứa số hiệu phiên bản của User và ReviewQueue
     */
    public Revision current(Long userId, boolean canReview) {
        AtomicLong userRevision = userRevisions.get(userId);
        return new Revision(userRevision == null ? 0 : userRevision.get(),
                canReview ? reviewRevision.get() : 0);
    }

    /**
     * Đánh dấu tài liệu có thay đổi sau khi giao dịch CSDL đã Commit thành công.
     * Tăng số hiệu phiên bản của tác giả tài liệu và của hàng đợi kiểm duyệt.
     *
     * @param ownerId Mã tác giả sở hữu tài liệu
     */
    public void documentChangedAfterCommit(Long ownerId) {
        afterCommit(() -> {
            if (ownerId != null) {
                userRevisions.computeIfAbsent(ownerId, ignored -> new AtomicLong()).incrementAndGet();
            }
            reviewRevision.incrementAndGet();
        });
    }

    /**
     * Đánh dấu có thay đổi dữ liệu đối với một người dùng sau khi giao dịch Commit thành công.
     *
     * @param userId Mã người dùng nhận tín hiệu thay đổi
     */
    public void changedForUserAfterCommit(Long userId) {
        if (userId == null) return;
        afterCommit(() -> userRevisions.computeIfAbsent(userId, ignored -> new AtomicLong()).incrementAndGet());
    }

    /**
     * Đăng ký đồng bộ thực thi tác vụ ngay sau khi transaction hiện hành commit thành công.
     * Nếu không có transaction nào thì thực thi ngay lập tức.
     *
     * @param change Tác vụ cần kích hoạt
     */
    private void afterCommit(Runnable change) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { change.run(); }
            });
        } else {
            change.run();
        }
    }

    /**
     * Bản ghi chứa cặp số hiệu phiên bản của User và Hàng đợi duyệt.
     */
    public record Revision(long user, long reviewQueue) { }
}
