package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.ApprovalHistory;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.ReviewAction;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface xử lý nghiệp vụ kiểm duyệt tài liệu.
 *
 * <p>Reviewer (hoặc Admin) thực hiện các hành động duyệt qua {@link ReviewAction}:</p>
 * <ul>
 *   <li>{@code APPROVE} – Duyệt tài liệu, chuyển trạng thái thành PUBLISHED.</li>
 *   <li>{@code REJECT} – Từ chối, chuyển thành REJECTED kèm lý do từ chối.</li>
 *   <li>{@code REQUEST_REVISION} – Yêu cầu người nộp chỉnh sửa thêm (REVISION_REQUESTED).</li>
 * </ul>
 *
 * <p>Mỗi hành động duyệt sẽ tạo một bản ghi {@link ApprovalHistory} bất biến (append-only)
 * để lưu lại toàn bộ lịch sử kiểm duyệt của tài liệu, phục vụ kiểm toán.</p>
 *
 * <p>Implementation: {@code ReviewServiceImpl}.</p>
 */
public interface ReviewService {

    /**
     * Thực hiện hành động kiểm duyệt tài liệu (không có điểm đánh giá chất lượng).
     *
     * @param documentId ID tài liệu cần duyệt.
     * @param action     Hành động duyệt (APPROVE / REJECT / REQUEST_REVISION).
     * @param comment    Nhận xét/lý do của reviewer.
     * @param reviewer   Người thực hiện kiểm duyệt.
     * @return Tài liệu sau khi được cập nhật trạng thái.
     */
    Document review(Long documentId, ReviewAction action, String comment, User reviewer);

    /**
     * Thực hiện hành động kiểm duyệt tài liệu kèm điểm đánh giá chất lượng.
     *
     * @param documentId                  ID tài liệu cần duyệt.
     * @param action                      Hành động duyệt (APPROVE / REJECT / REQUEST_REVISION).
     * @param comment                     Nhận xét/lý do của reviewer.
     * @param reviewer                    Người thực hiện kiểm duyệt.
     * @param contentQualityScore         Điểm chất lượng nội dung (1–5, có thể null).
     * @param teachingEffectivenessScore  Điểm hiệu quả giảng dạy (1–5, có thể null).
     * @param easeOfUseScore              Điểm dễ sử dụng (1–5, có thể null).
     * @return Tài liệu sau khi được cập nhật trạng thái.
     */
    Document review(Long documentId, ReviewAction action, String comment, User reviewer,
                    Integer contentQualityScore, Integer teachingEffectivenessScore, Integer easeOfUseScore);

    /**
     * Lấy toàn bộ lịch sử kiểm duyệt của tài liệu (không phân trang).
     *
     * @param documentId ID tài liệu.
     * @return Danh sách bản ghi lịch sử kiểm duyệt theo thứ tự thời gian.
     */
    List<ApprovalHistory> history(Long documentId);

    /**
     * Lấy lịch sử kiểm duyệt của tài liệu có phân trang.
     *
     * @param documentId ID tài liệu.
     * @param pageable   Thông tin phân trang.
     * @return Trang kết quả lịch sử kiểm duyệt.
     */
    Page<ApprovalHistory> history(Long documentId, Pageable pageable);
}
