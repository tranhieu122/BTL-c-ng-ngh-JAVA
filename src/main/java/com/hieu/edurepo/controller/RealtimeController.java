package com.hieu.edurepo.controller;

import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.RealtimeService;
import com.hieu.edurepo.service.RealtimeSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Objects;

/**
 * Bộ điều hướng kết nối thời gian thực Server-Sent Events (Realtime Controller).
 * <p>
 * Cung cấp luồng streaming sự kiện /events/stream và truy vấn ảnh chụp trạng thái tức thời /events/snapshot.
 * Giúp giao diện cập nhật ngay lập tức các biến động: có thông báo mới, bài được duyệt, đổi avatar mà không cần reload trang.
 * </p>
 */
@RestController
@RequestMapping("/events")
public class RealtimeController {

    /**
     * Bắt ngoại lệ khi client (trình duyệt) chủ động đóng kết nối stream SSE.
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void disconnected() {
        // Trình duyệt đã ngắt kết nối; bỏ qua an toàn vì không thể render trang lỗi trên kết nối đã đóng.
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Void> unavailable(org.springframework.web.server.ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).build();
    }

    private final RealtimeService realtime;
    private final RealtimeSnapshotService snapshots;

    public RealtimeController(RealtimeService realtime, RealtimeSnapshotService snapshots) {
        this.realtime = realtime;
        this.snapshots = snapshots;
    }

    /**
     * Khởi tạo kết nối SSE (Server-Sent Events) lâu dài giữa trình duyệt và máy chủ.
     *
     * @param principal Người dùng đã đăng nhập
     * @param request Yêu cầu HTTP chứa phiên session
     * @param response Phản hồi HTTP thiết lập cờ tắt cache và tắt proxy buffering
     * @return Đối tượng SseEmitter duy trì kênh truyền dữ liệu
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@AuthenticationPrincipal CustomUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        // Tắt bộ nhớ đệm trình duyệt và tắt bộ đệm Nginx/Proxy để gói tin SSE được truyền phát ngay lập tức
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");

        return realtime.connect(principal, Objects.requireNonNull(request.getSession(false),
                "Yêu cầu kết nối thời gian thực bắt buộc phải có phiên đăng nhập hợp lệ"));
    }

    /**
     * Lấy ảnh chụp trạng thái đầy đủ (Snapshot) của người dùng hiện tại (hồ sơ, bài viết, số thông báo).
     *
     * @param principal Người dùng đăng nhập
     * @param response Phản hồi HTTP
     * @return Đối tượng Snapshot chứa dữ liệu đồng bộ
     */
    @GetMapping("/snapshot")
    public RealtimeSnapshotService.Snapshot snapshot(@AuthenticationPrincipal CustomUserPrincipal principal, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return snapshots.snapshot(principal);
    }
}
