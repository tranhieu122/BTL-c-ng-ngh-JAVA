package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.DocumentDownloadHistory;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.DocumentViewHistory;

import java.util.List;

/**
 * Dịch vụ theo dõi và thống kê hoạt động học tập của người dùng (xem bài, tải bài, đánh giá).
 */
public interface UserActivityService {

    /**
     * Ghi nhận một lượt xem tài liệu của người dùng (có cơ chế chống spam tăng view liên tục).
     *
     * @param userId Mã người dùng
     * @param documentId Mã tài liệu vừa xem
     */
    void recordView(Long userId, Long documentId);

    /**
     * Ghi nhận một lượt tải tài liệu thành công về máy tính.
     *
     * @param userId Mã người dùng tải
     * @param documentId Mã tài liệu được tải
     */
    void recordDownload(Long userId, Long documentId);

    /**
     * Lấy danh sách các tài liệu người dùng vừa mở xem gần đây nhất.
     *
     * @param userId Mã người dùng
     * @return Danh sách lịch sử xem bài
     */
    List<DocumentViewHistory> recentlyViewed(Long userId);

    /**
     * Lấy toàn bộ lịch sử các lượt tải học liệu của người dùng.
     *
     * @param userId Mã người dùng
     * @return Danh sách lịch sử tải tệp
     */
    List<DocumentDownloadHistory> downloadHistory(Long userId);

    /**
     * Lấy danh sách các bài viết mà người dùng đã từng để lại nhận xét, chấm điểm.
     *
     * @param userId Mã người dùng
     * @return Danh sách đánh giá đã gửi
     */
    List<DocumentReview> reviews(Long userId);
}
