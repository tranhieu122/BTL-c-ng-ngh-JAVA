package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.ChatMessageDto;
import com.hieu.edurepo.dto.ChatSessionDto;
import com.hieu.edurepo.dto.CreateChatSessionRequest;
import com.hieu.edurepo.dto.GuestMigrationRequest;
import com.hieu.edurepo.dto.UpdateChatSessionTitleRequest;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.ChatSessionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controller REST API quản lý các phiên hội thoại đa phiên (Multi-Session Conversation Management) của EduBot.
 * Cung cấp đầy đủ cơ chế bảo vệ IDOR và cô lập dữ liệu người dùng.
 */
@RestController
@RequestMapping("/api/chat-sessions")
public class ChatSessionController {

    private final ChatSessionService sessionService;

    public ChatSessionController(ChatSessionService sessionService) {
        this.sessionService = sessionService;
    }

    private CustomUserPrincipal getPrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal p) {
            return p;
        }
        return null;
    }

    /**
     * Lấy danh sách các phiên trò chuyện của người dùng hiện tại (sắp xếp mới nhất lên đầu).
     */
    @GetMapping
    public ResponseEntity<?> getMySessions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập để xem lịch sử phiên trò chuyện."
            ));
        }

        List<ChatSessionDto> sessions = sessionService.getUserSessions(
                principal.getId(), PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(sessions);
    }

    /**
     * Tạo một phiên trò chuyện mới cho người dùng.
     */
    @PostMapping
    public ResponseEntity<?> createSession(
            Authentication authentication,
            @Valid @RequestBody CreateChatSessionRequest request) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập để tạo phiên trò chuyện."
            ));
        }

        ChatSessionDto created = sessionService.createSession(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lấy chi tiết một phiên trò chuyện (kiểm tra quyền sở hữu chống IDOR).
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSession(
            @PathVariable Long id,
            Authentication authentication) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập."
            ));
        }

        ChatSessionDto session = sessionService.getSession(id, principal.getId());
        return ResponseEntity.ok(session);
    }

    /**
     * Lấy danh sách tin nhắn của một phiên trò chuyện (kiểm tra quyền sở hữu chống IDOR).
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<?> getSessionMessages(
            @PathVariable Long id,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập."
            ));
        }

        List<ChatMessageDto> messages = sessionService.getSessionMessages(
                id, principal.getId(), PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(messages);
    }

    /**
     * Đổi tên một phiên trò chuyện.
     */
    @PatchMapping("/{id}/title")
    public ResponseEntity<?> updateTitle(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateChatSessionTitleRequest request) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập."
            ));
        }

        ChatSessionDto updated = sessionService.updateSessionTitle(id, principal.getId(), request.title());
        return ResponseEntity.ok(updated);
    }

    /**
     * Xóa mềm (Soft Delete: ACTIVE -> DELETED) một phiên trò chuyện.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSession(
            @PathVariable Long id,
            Authentication authentication) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập."
            ));
        }

        sessionService.deleteSession(id, principal.getId());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa phiên trò chuyện thành công."
        ));
    }

    /**
     * Ghi nhận đánh giá phản hồi (Thumbs Up / Down) cho một tin nhắn trong session.
     */
    @PostMapping("/{id}/messages/{messageId}/feedback")
    public ResponseEntity<?> submitMessageFeedback(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestBody Map<String, Integer> payload,
            Authentication authentication) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập để đánh giá."
            ));
        }

        Integer rating = payload != null ? payload.get("rating") : null;
        sessionService.updateMessageFeedback(id, messageId, principal.getId(), rating);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    /**
     * Đồng bộ/chuyển giao các phiên trò chuyện từ guest storage (localStorage) vào tài khoản khi người dùng đăng nhập.
     * Đảm bảo Idempotent.
     */
    @PostMapping("/import-guest")
    public ResponseEntity<?> importGuestSessions(
            Authentication authentication,
            @RequestBody GuestMigrationRequest request) {
        CustomUserPrincipal principal = getPrincipal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Vui lòng đăng nhập để đồng bộ cuộc trò chuyện."
            ));
        }

        int count = sessionService.migrateGuestSessions(principal.getId(), request);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "importedCount", count,
                "message", "Đã đồng bộ thành công " + count + " cuộc trò chuyện vào tài khoản của bạn."
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "FORBIDDEN",
                "message", "Bạn không có quyền truy cập phiên trò chuyện này."
        ));
    }
}
