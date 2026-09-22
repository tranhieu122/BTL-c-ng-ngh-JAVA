package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.DocumentReviewForm;
import com.hieu.edurepo.dto.DocumentReviewSummary;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Dịch vụ đánh giá, thẩm định và bình luận học liệu từ Giảng viên kiểm duyệt và người học.
 */
public interface DocumentReviewService {

    /**
     * Tạo mới nhận xét hoặc đánh giá sao cho tài liệu.
     *
     * @param documentId Mã tài liệu
     * @param user Người dùng gửi đánh giá
     * @param form Dữ liệu điểm số và nhận xét
     * @return Bản ghi đánh giá vừa lưu
     */
    DocumentReview create(Long documentId, User user, DocumentReviewForm form);

    /**
     * Chỉnh sửa nhận xét đã gửi trước đó của chính người dùng.
     *
     * @param documentId Mã tài liệu
     * @param reviewId Mã nhận xét cần sửa
     * @param user Người dùng thực hiện sửa
     * @param form Nội dung cập nhật
     * @return Bản ghi đánh giá sau khi sửa
     */
    DocumentReview update(Long documentId, Long reviewId, User user, DocumentReviewForm form);

    /**
     * Xóa nhận xét đánh giá khỏi tài liệu.
     *
     * @param documentId Mã tài liệu
     * @param reviewId Mã nhận xét cần xóa
     * @param user Người dùng yêu cầu xóa (chính chủ hoặc Admin)
     */
    void delete(Long documentId, Long reviewId, User user);

    /**
     * Tìm nhận xét đánh giá của một người dùng cụ thể đối với tài liệu (nếu có).
     *
     * @param documentId Mã tài liệu
     * @param userId Mã người dùng
     * @return Đánh giá của người dùng
     */
    Optional<DocumentReview> findOwn(Long documentId, Long userId);

    /**
     * Lấy danh sách các nhận xét công khai không bị ẩn của tài liệu.
     *
     * @param documentId Mã tài liệu
     * @return Danh sách bình luận hiển thị
     */
    List<DocumentReview> visibleComments(Long documentId);

    /**
     * Tính toán bảng tổng kết đánh giá (điểm sao trung bình, số lượng đánh giá) của tài liệu.
     *
     * @param documentId Mã tài liệu
     * @return Đối tượng tóm tắt đánh giá
     */
    DocumentReviewSummary summary(Long documentId);

    /**
     * Lấy toàn bộ nhận xét trong hệ thống phục vụ công tác kiểm duyệt nội dung của Quản trị viên.
     * @return Danh sách tất cả nhận xét
     */
    List<DocumentReview> findAllForModeration();

    /**
     * Thiết lập ẩn hoặc hiện một nhận xét vi phạm tiêu chuẩn cộng đồng.
     *
     * @param reviewId Mã nhận xét
     * @param hidden true nếu muốn ẩn, false để hiển thị lại
     */
    void setHidden(Long reviewId, boolean hidden);
}
