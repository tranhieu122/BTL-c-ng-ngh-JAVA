package com.hieu.edurepo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-process change signal used to avoid database polling for unchanged SSE connections. */
@Service
public class RealtimeChangeTracker {
    private final AtomicLong reviewRevision = new AtomicLong();
    private final ConcurrentHashMap<Long, AtomicLong> userRevisions = new ConcurrentHashMap<>();

    public Revision current(Long userId, boolean canReview) {
        AtomicLong userRevision = userRevisions.get(userId);
        return new Revision(userRevision == null ? 0 : userRevision.get(),
                canReview ? reviewRevision.get() : 0);
    }

    public void documentChangedAfterCommit(Long ownerId) {
        afterCommit(() -> {
            if (ownerId != null) {
                userRevisions.computeIfAbsent(ownerId, ignored -> new AtomicLong()).incrementAndGet();
            }
            reviewRevision.incrementAndGet();
        });
    }

    public void changedForUserAfterCommit(Long userId) {
        if (userId == null) return;
        afterCommit(() -> userRevisions.computeIfAbsent(userId, ignored -> new AtomicLong()).incrementAndGet());
    }

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

    public record Revision(long user, long reviewQueue) { }
}
