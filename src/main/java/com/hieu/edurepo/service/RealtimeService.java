package com.hieu.edurepo.service;

import com.hieu.edurepo.security.CustomUserPrincipal;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sends authoritative database snapshots; reconnects never depend on an in-memory event history. */
@Service
public class RealtimeService {
    private final RealtimeSnapshotService snapshots;
    private final long interval;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, Thread.ofPlatform().daemon().name("realtime-", 0).factory());
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    public RealtimeService(RealtimeSnapshotService snapshots, @Value("${app.realtime.interval-ms:2000}") long interval) {
        this.snapshots = snapshots; this.interval = Math.max(100, interval);
    }
    public synchronized SseEmitter connect(CustomUserPrincipal principal, HttpSession session) {
        snapshots.snapshot(principal); // authorize before committing the streaming response
        if (connections.size() >= 500 || connections.stream().filter(c -> c.principal.getId().equals(principal.getId())).count() >= 5)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều kết nối đang mở.");
        var connection = new Connection(principal, session);
        connections.add(connection);
        connection.emitter.onCompletion(connection::close);
        connection.emitter.onTimeout(connection::close);
        connection.emitter.onError(error -> connection.close());
        connection.task = scheduler.scheduleWithFixedDelay(connection::tick, 100, interval, TimeUnit.MILLISECONDS);
        return connection.emitter;
    }
    @PreDestroy public void shutdown() { connections.forEach(Connection::close); scheduler.shutdownNow(); }
    private final class Connection {
        final CustomUserPrincipal principal;
        final HttpSession session;
        final SseEmitter emitter = new SseEmitter(60_000L);
        final AtomicBoolean closed = new AtomicBoolean();
        volatile ScheduledFuture<?> task;
        RealtimeSnapshotService.Snapshot previous;
        long heartbeat;
        final long started = System.currentTimeMillis();
        long sequence;
        Connection(CustomUserPrincipal principal, HttpSession session) { this.principal = principal; this.session = session; }
        void tick() {
            if (closed.get()) return;
            try {
                if (session.getMaxInactiveInterval() > 0 && System.currentTimeMillis() - session.getLastAccessedTime() > session.getMaxInactiveInterval() * 1000L)
                    throw new AccessDeniedException("Session expired");
                var current = snapshots.snapshot(principal);
                if (!Objects.equals(previous, current)) {
                    emitter.send(SseEmitter.event().name("snapshot").id(Long.toString(++sequence)).reconnectTime(2000).data(current));
                    previous = current; heartbeat = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - heartbeat >= 15_000) {
                    emitter.send(SseEmitter.event().comment("keepalive")); heartbeat = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - started >= 55_000) close();
            } catch (AccessDeniedException | IllegalStateException expired) {
                try { emitter.send(SseEmitter.event().name("session-expired").data("expired")); } catch (Exception ignored) { }
                close();
            } catch (Exception failure) {
                // Network/DB failures are recoverable by reconnect; never serialize exception details.
                close();
            }
        }
        void close() {
            if (closed.compareAndSet(false, true)) {
                if (task != null) task.cancel(false);
                connections.remove(this); emitter.complete();
            }
        }
    }
}
