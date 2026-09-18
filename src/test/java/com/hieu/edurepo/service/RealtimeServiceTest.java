package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ProfileView;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.hieu.edurepo.observability.OperationalMetrics;

import java.util.List;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeServiceTest {
    @Test
    void unchangedConnectionsDoNotRepeatedlyQueryDatabaseButChangesTriggerRefresh() {
        var snapshots = mock(RealtimeSnapshotService.class);
        var changes = new RealtimeChangeTracker();
        var account = new User();
        account.setId(42L); account.setEmail("realtime@example.test"); account.setPassword("hash");
        account.setFullName("Realtime User"); account.setEnabled(true);
        var principal = CustomUserPrincipal.from(account);
        var revision = changes.current(account.getId(), false);
        var snapshot = new RealtimeSnapshotService.Snapshot(
                new ProfileView(account.getFullName(), account.getEmail(), null, null, null, "default"),
                List.of(), List.of(), false, revision);
        when(snapshots.snapshot(principal)).thenReturn(snapshot,
                new RealtimeSnapshotService.Snapshot(snapshot.profile(), List.of(), List.of(), false,
                        new RealtimeChangeTracker.Revision(1, 0)));
        HttpSession session = mock(HttpSession.class);
        when(session.getMaxInactiveInterval()).thenReturn(0);
        var service = new RealtimeService(snapshots, changes, 100, 60_000);

        try {
            service.connect(principal, session);
            verify(snapshots, after(450).times(1)).snapshot(principal);

            changes.changedForUserAfterCommit(account.getId());
            verify(snapshots, timeout(1_000).times(2)).snapshot(principal);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void activeConnectionGaugeReturnsToZeroOnShutdown() {
        var snapshots = mock(RealtimeSnapshotService.class);
        var changes = new RealtimeChangeTracker();
        var account = new User();
        account.setId(7L); account.setEmail("metrics@example.test"); account.setPassword("hash");
        account.setFullName("Metrics User"); account.setEnabled(true);
        var principal = CustomUserPrincipal.from(account);
        var snapshot = new RealtimeSnapshotService.Snapshot(
                new ProfileView(account.getFullName(), account.getEmail(), null, null, null, "default"),
                List.of(), List.of(), false, changes.current(account.getId(), false));
        when(snapshots.snapshot(principal)).thenReturn(snapshot);
        HttpSession session = mock(HttpSession.class);
        when(session.getMaxInactiveInterval()).thenReturn(0);
        var registry = new SimpleMeterRegistry();
        var service = new RealtimeService(snapshots, changes, 1_000, 60_000,
                new OperationalMetrics(registry));

        service.connect(principal, session);
        org.junit.jupiter.api.Assertions.assertEquals(1.0,
                registry.get("edurepo.realtime.connections.active").gauge().value());
        service.shutdown();
        org.junit.jupiter.api.Assertions.assertEquals(0.0,
                registry.get("edurepo.realtime.connections.active").gauge().value());
        org.junit.jupiter.api.Assertions.assertEquals(1.0,
                registry.get("edurepo.realtime.connections.closed").tag("reason", "SHUTDOWN").counter().count());
    }
}
