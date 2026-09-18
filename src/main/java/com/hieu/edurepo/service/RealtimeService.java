package com.hieu.edurepo.service;

import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.observability.OperationalMetrics;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Objects;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Sends authoritative database snapshots; reconnects never depend on an in-memory event history. */
@Service
public class RealtimeService {
    private final RealtimeSnapshotService snapshots;
    private final RealtimeChangeTracker changes;
    private final long interval;
    private final long safetyRefresh;
    private final OperationalMetrics metrics;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, new ThreadFactory() {
        private final ThreadFactory delegate = Executors.defaultThreadFactory();
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = delegate.newThread(task);
            thread.setDaemon(true);
            thread.setName("realtime-" + count.getAndIncrement());
            return thread;
        }
    });
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    public RealtimeService(RealtimeSnapshotService snapshots, RealtimeChangeTracker changes,
                           @Value("${app.realtime.interval-ms:2000}") long interval,
                           @Value("${app.realtime.safety-refresh-ms:30000}") long safetyRefresh) {
        this(snapshots, changes, interval, safetyRefresh,
                new OperationalMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RealtimeService(RealtimeSnapshotService snapshots, RealtimeChangeTracker changes,
                           @Value("${app.realtime.interval-ms:2000}") long interval,
                           @Value("${app.realtime.safety-refresh-ms:30000}") long safetyRefresh,
                           OperationalMetrics metrics) {
        this.snapshots = snapshots;
        this.changes = changes;
        this.interval = Math.max(100, interval);
        this.safetyRefresh = Math.max(this.interval, safetyRefresh);
        this.metrics = metrics;
    }
    public synchronized SseEmitter connect(CustomUserPrincipal principal, HttpSession session) {
        // Lấy snapshot ngay trước khi mở stream để xác thực quyền và phát hiện session/account không hợp lệ.
        long snapshotStarted = System.nanoTime();
        var initial = snapshots.snapshot(principal); // authorize before committing the streaming response
        metrics.realtimeSnapshot(Duration.ofNanos(System.nanoTime() - snapshotStarted));
        // Giới hạn số kết nối giúp tránh một tài khoản hoặc toàn hệ thống mở quá nhiều SSE connection.
        if (connections.size() >= 500) {
            metrics.sseRejected("SYSTEM_LIMIT");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều kết nối đang mở.");
        }
        if (connections.stream().filter(c -> c.principal.getId().equals(principal.getId())).count() >= 5) {
            metrics.sseRejected("USER_LIMIT");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều kết nối đang mở.");
        }
        var connection = new Connection(principal, session, initial);
        connections.add(connection);
        metrics.sseOpened();
        connection.emitter.onCompletion(() -> connection.close("CLIENT"));
        connection.emitter.onTimeout(() -> connection.close("TIMEOUT"));
        connection.emitter.onError(error -> connection.close("SEND_ERROR"));
        connection.task = scheduler.scheduleWithFixedDelay(connection::tick, 100, interval, TimeUnit.MILLISECONDS);
        return connection.emitter;
    }
    @PreDestroy public void shutdown() {
        connections.forEach(connection -> connection.close("SHUTDOWN"));
        scheduler.shutdownNow();
    }
    private final class Connection {
        final CustomUserPrincipal principal;
        final HttpSession session;
        final SseEmitter emitter = new SseEmitter(60_000L);
        final AtomicBoolean closed = new AtomicBoolean();
        volatile ScheduledFuture<?> task;
        RealtimeSnapshotService.Snapshot previous;
        long heartbeat;
        long lastSnapshotAt;
        final long started = System.currentTimeMillis();
        long sequence;
        RealtimeChangeTracker.Revision observedRevision;
        final RealtimeSnapshotService.Snapshot initial;
        Connection(CustomUserPrincipal principal, HttpSession session, RealtimeSnapshotService.Snapshot initial) {
            this.principal = principal; this.session = session; this.initial = initial;
            this.observedRevision = initial.revision();
        }
        void tick() {
            if (closed.get()) return;
            try {
                // SSE là kết nối dài, nên tự kiểm tra thời hạn session thay vì chờ request mới.
                if (session.getMaxInactiveInterval() > 0 && System.currentTimeMillis() - session.getLastAccessedTime() > session.getMaxInactiveInterval() * 1000L)
                    throw new AccessDeniedException("Session expired");
                long now = System.currentTimeMillis();
                boolean canReview = principal.getAuthorities().stream().anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN") || authority.getAuthority().equals("ROLE_REVIEWER"));
                var latestRevision = changes.current(principal.getId(), canReview);
                boolean revisionChanged = !Objects.equals(observedRevision, latestRevision);
                boolean safetyRefreshDue = previous != null && !revisionChanged
                        && now - lastSnapshotAt >= safetyRefresh;
                boolean refresh = previous == null || revisionChanged || safetyRefreshDue;
                RealtimeSnapshotService.Snapshot current = null;
                if (refresh) {
                    if (previous == null) {
                        current = initial;
                    } else {
                        long snapshotStarted = System.nanoTime();
                        current = snapshots.snapshot(principal);
                        metrics.realtimeSnapshot(Duration.ofNanos(System.nanoTime() - snapshotStarted));
                    }
                    if (safetyRefreshDue) metrics.realtimeSafetyRefresh();
                    observedRevision = latestRevision;
                    lastSnapshotAt = now;
                } else {
                    metrics.realtimeDatabaseSkipped();
                }
                if (current != null && !Objects.equals(previous, current)) {
                    // Chỉ gửi snapshot khi dữ liệu thay đổi để giảm traffic và tránh render lại không cần thiết.
                    emitter.send(SseEmitter.event().name("snapshot").id(Long.toString(++sequence)).reconnectTime(2000).data(current));
                    metrics.realtimeEventSent();
                    previous = current; heartbeat = now;
                } else if (now - heartbeat >= 15_000) {
                    // Keepalive giữ kết nối qua proxy/browser và giúp client biết stream vẫn sống.
                    emitter.send(SseEmitter.event().comment("keepalive")); heartbeat = System.currentTimeMillis();
                }
                if (now - started >= 55_000) close("ROTATION");
            } catch (AccessDeniedException | IllegalStateException expired) {
                // Không gửi chi tiết lỗi bảo mật; client chỉ cần biết phải đăng nhập lại/đồng bộ lại.
                try { emitter.send(SseEmitter.event().name("session-expired").data("expired")); } catch (Exception ignored) { }
                close("SESSION_EXPIRED");
            } catch (Exception failure) {
                // Network/DB failures are recoverable by reconnect; never serialize exception details.
                close("SEND_ERROR");
            }
        }
        void close(String reason) {
            if (closed.compareAndSet(false, true)) {
                if (task != null) task.cancel(false);
                connections.remove(this);
                metrics.sseClosed(reason);
                emitter.complete();
            }
        }
    }
}
