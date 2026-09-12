package com.hieu.edurepo.controller;

import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.RealtimeService;
import com.hieu.edurepo.service.RealtimeSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/events")
public class RealtimeController {
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void disconnected() {
        // The browser closed the stream; its committed response cannot render an error page.
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Void> unavailable(org.springframework.web.server.ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).build();
    }
    private final RealtimeService realtime;
    private final RealtimeSnapshotService snapshots;
    public RealtimeController(RealtimeService realtime, RealtimeSnapshotService snapshots) { this.realtime = realtime; this.snapshots = snapshots; }
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@AuthenticationPrincipal CustomUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Accel-Buffering", "no");
        return realtime.connect(principal, request.getSession(false));
    }
    @GetMapping("/snapshot")
    public RealtimeSnapshotService.Snapshot snapshot(@AuthenticationPrincipal CustomUserPrincipal principal, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return snapshots.snapshot(principal);
    }
}
