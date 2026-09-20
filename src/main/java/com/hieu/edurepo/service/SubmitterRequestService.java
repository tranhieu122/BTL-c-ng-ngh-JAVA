package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.SubmitterRequest;

import java.util.List;

/**
 * Service interface quản lý quy trình yêu cầu nâng cấp quyền lên Submitter.
 *
 * <p>Người dùng thường (USER) cần đủ điều kiện mới có thể nộp tài liệu lên hệ thống.
 * Quy trình:</p>
 * <ol>
 *   <li>Người dùng nộp đơn xin quyền Submitter kèm lý do.</li>
 *   <li>Admin xem xét và phê duyệt hoặc từ chối.</li>
 *   <li>Khi phê duyệt: vai trò SUBMITTER được gán tự động cho tài khoản người dùng.</li>
 * </ol>
 *
 * <p>Mỗi người dùng chỉ có thể có một yêu cầu đang chờ xử lý ({@link #hasPending(Long)}).</p>
 *
 * <p>Implementation: {@code SubmitterRequestServiceImpl}.</p>
 */
public interface SubmitterRequestService {

    /**
     * Tạo yêu cầu nâng quyền Submitter mới.
     * Một người dùng chỉ được tạo khi chưa có yêu cầu PENDING nào.
     *
     * @param requesterId ID người dùng nộp đơn.
     * @param reason      Lý do muốn trở thành Submitter.
     * @return Yêu cầu vừa tạo với trạng thái PENDING.
     * @throws IllegalStateException Nếu đã có yêu cầu đang chờ xử lý.
     */
    SubmitterRequest create(Long requesterId, String reason);

    /**
     * Lấy danh sách tất cả yêu cầu của một người dùng (kể cả đã xử lý).
     *
     * @param requesterId ID người dùng.
     * @return Danh sách yêu cầu của người dùng.
     */
    List<SubmitterRequest> findForUser(Long requesterId);

    /**
     * Lấy toàn bộ yêu cầu trong hệ thống (dành cho admin).
     *
     * @return Danh sách tất cả yêu cầu.
     */
    List<SubmitterRequest> findAll();

    /**
     * Kiểm tra người dùng có yêu cầu đang chờ xử lý không.
     *
     * @param requesterId ID người dùng.
     * @return {@code true} nếu có yêu cầu PENDING.
     */
    boolean hasPending(Long requesterId);

    /**
     * Phê duyệt yêu cầu nâng quyền Submitter.
     * Sau khi phê duyệt, tài khoản người dùng được gán thêm vai trò SUBMITTER.
     *
     * @param requestId ID yêu cầu cần phê duyệt.
     * @param adminId   ID admin thực hiện phê duyệt.
     * @return Yêu cầu với trạng thái APPROVED.
     */
    SubmitterRequest approve(Long requestId, Long adminId);

    /**
     * Từ chối yêu cầu nâng quyền Submitter.
     *
     * @param requestId       ID yêu cầu cần từ chối.
     * @param adminId         ID admin thực hiện từ chối.
     * @param rejectionReason Lý do từ chối (lưu vào bản ghi).
     * @return Yêu cầu với trạng thái REJECTED.
     */
    SubmitterRequest reject(Long requestId, Long adminId, String rejectionReason);
}
