package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentVersion;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.LicenseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface quản lý vòng đời tài liệu học liệu trong EduRepo.
 *
 * <p>Định nghĩa các nghiệp vụ cốt lõi: tìm kiếm, tạo/cập nhật bản nháp,
 * nộp duyệt, ghi nhận thống kê (xem/tải), và quản lý lịch sử phiên bản.</p>
 *
 * <p>Implementation: {@code DocumentServiceImpl}.</p>
 */
public interface DocumentService {

    /**
     * Tìm tài liệu theo ID. Ném {@code ResourceNotFoundException} nếu không tồn tại.
     *
     * @param id ID tài liệu.
     * @return Đối tượng {@link Document}.
     */
    Document findById(Long id);

    /**
     * Lấy danh sách tất cả tài liệu của một người dùng (không phân trang).
     *
     * @param userId ID người dùng.
     * @return Danh sách tài liệu thuộc về người dùng.
     */
    List<Document> findByOwner(Long userId);

    /**
     * Tìm kiếm tài liệu của người dùng với bộ lọc đa tiêu chí (phân trang).
     *
     * @param userId       ID người dùng.
     * @param keyword      Từ khóa tìm kiếm.
     * @param status       Trạng thái tài liệu (null = tất cả).
     * @param categoryId   ID danh mục (null = tất cả).
     * @param facultyId    ID khoa (null = tất cả).
     * @param departmentId ID bộ môn (null = tất cả).
     * @param pageable     Thông tin phân trang và sắp xếp.
     * @return Trang kết quả.
     */
    Page<Document> searchByOwner(Long userId, String keyword, DocumentStatus status, Long categoryId,
                                 Long facultyId, Long departmentId, Pageable pageable);

    /**
     * Lấy tất cả tài liệu đang chờ duyệt (PENDING_REVIEW).
     *
     * @return Danh sách tài liệu chờ duyệt.
     */
    List<Document> findPendingReview();

    /**
     * Tìm kiếm tài liệu trong hàng đợi duyệt (phân trang).
     *
     * @param keyword  Từ khóa tìm kiếm.
     * @param status   Trạng thái cụ thể (null = tất cả trạng thái chờ duyệt).
     * @param pageable Thông tin phân trang.
     * @return Trang kết quả.
     */
    Page<Document> searchPendingReview(String keyword, DocumentStatus status, Pageable pageable);

    /**
     * Đếm số tài liệu đang chờ duyệt theo trạng thái.
     *
     * @param status Trạng thái cần đếm.
     * @return Số lượng tài liệu.
     */
    long countPendingReview(DocumentStatus status);

    /**
     * Tìm kiếm tài liệu đã công bố (PUBLISHED) theo từ khóa (phân trang đơn giản).
     *
     * @param keyword  Từ khóa tìm kiếm.
     * @param pageable Thông tin phân trang.
     * @return Trang kết quả.
     */
    Page<Document> searchPublished(String keyword, Pageable pageable);

    /**
     * Tìm kiếm tài liệu đã công bố với đầy đủ bộ lọc.
     *
     * @param keyword        Từ khóa tìm kiếm.
     * @param categoryId     ID danh mục (null = tất cả).
     * @param resourceType   Loại tài nguyên học tập (null = tất cả).
     * @param educationLevel Cấp học (null = tất cả).
     * @param licenseType    Loại bản quyền (null = tất cả).
     * @param languageCode   Mã ngôn ngữ (rỗng = tất cả).
     * @param pageable       Thông tin phân trang.
     * @return Trang kết quả.
     */
    Page<Document> searchPublished(String keyword, Long categoryId, LearningResourceType resourceType,
                                   EducationLevel educationLevel, LicenseType licenseType,
                                   String languageCode, Pageable pageable);

    /**
     * Lấy danh sách tài liệu liên quan đã công bố (dùng cho gợi ý tài liệu tương tự).
     *
     * @param document Tài liệu gốc.
     * @param limit    Số lượng tài liệu liên quan tối đa.
     * @return Danh sách tài liệu liên quan.
     */
    List<Document> findRelatedPublished(Document document, int limit);

    /**
     * Lấy tất cả phiên bản của tài liệu (không phân trang).
     *
     * @param documentId ID tài liệu.
     * @return Danh sách phiên bản theo thứ tự giảm dần.
     */
    List<DocumentVersion> findVersions(Long documentId);

    /**
     * Lấy danh sách phiên bản của tài liệu có phân trang.
     *
     * @param documentId ID tài liệu.
     * @param pageable   Thông tin phân trang.
     * @return Trang danh sách phiên bản.
     */
    Page<DocumentVersion> findVersions(Long documentId, Pageable pageable);

    /**
     * Lấy một phiên bản cụ thể của tài liệu.
     *
     * @param documentId ID tài liệu.
     * @param versionId  ID phiên bản.
     * @return Đối tượng {@link DocumentVersion}.
     */
    DocumentVersion findVersion(Long documentId, Long versionId);

    /**
     * Lấy danh sách đường dẫn file của tất cả phiên bản tài liệu (dùng để xóa file vật lý).
     *
     * @param documentId ID tài liệu.
     * @return Danh sách đường dẫn file.
     */
    List<String> versionFilePaths(Long documentId);

    /**
     * Ghi nhận một lượt xem tài liệu (tăng viewCount).
     *
     * @param documentId ID tài liệu được xem.
     */
    void recordView(Long documentId);

    /**
     * Ghi nhận một lượt tải xuống tài liệu (tăng downloadCount).
     *
     * @param documentId ID tài liệu được tải.
     */
    void recordDownload(Long documentId);

    /**
     * Lưu tài liệu mới ở trạng thái DRAFT.
     *
     * @param document Thông tin tài liệu.
     * @param owner    Người tạo tài liệu.
     * @return Tài liệu đã lưu.
     */
    Document saveDraft(Document document, User owner);

    /**
     * Lưu và nộp tài liệu mới ngay (không qua bước lưu nháp).
     *
     * @param document Thông tin tài liệu.
     * @param owner    Người tạo tài liệu.
     * @return Tài liệu đã nộp với trạng thái PENDING_REVIEW.
     */
    Document submitNew(Document document, User owner);

    /**
     * Cập nhật tài liệu đang ở trạng thái DRAFT.
     *
     * @param documentId ID tài liệu cần cập nhật.
     * @param changes    Các thay đổi cần áp dụng.
     * @param owner      Chủ sở hữu tài liệu (kiểm tra quyền).
     * @return Tài liệu đã cập nhật.
     */
    Document updateDraft(Long documentId, Document changes, User owner);

    /**
     * Cập nhật tài liệu đang ở trạng thái DRAFT kèm ghi chú thay đổi.
     *
     * @param documentId ID tài liệu cần cập nhật.
     * @param changes    Các thay đổi cần áp dụng.
     * @param owner      Chủ sở hữu tài liệu (kiểm tra quyền).
     * @param changeNote Ghi chú mô tả nội dung thay đổi (lưu vào lịch sử phiên bản).
     * @return Tài liệu đã cập nhật.
     */
    Document updateDraft(Long documentId, Document changes, User owner, String changeNote);

    /**
     * Nộp tài liệu DRAFT hoặc REVISION_REQUESTED để chờ duyệt.
     *
     * @param documentId ID tài liệu.
     * @param owner      Chủ sở hữu tài liệu.
     * @return Tài liệu với trạng thái PENDING_REVIEW.
     */
    Document submit(Long documentId, User owner);

    /**
     * Xóa tài liệu ở trạng thái DRAFT (chỉ chủ sở hữu mới có thể xóa).
     *
     * @param documentId ID tài liệu cần xóa.
     * @param owner      Chủ sở hữu tài liệu.
     */
    void deleteDraft(Long documentId, User owner);
}
