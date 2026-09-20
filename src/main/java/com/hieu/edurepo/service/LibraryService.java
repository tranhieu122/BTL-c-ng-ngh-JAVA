package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Bookmark;
import com.hieu.edurepo.entity.CollectionItem;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentCollection;
import com.hieu.edurepo.entity.User;

import java.util.List;

/**
 * Service interface quản lý thư viện cá nhân của người dùng trong EduRepo.
 *
 * <p>Cung cấp các tính năng:</p>
 * <ul>
 *   <li><strong>Bookmark</strong>: Đánh dấu/bỏ đánh dấu tài liệu yêu thích.</li>
 *   <li><strong>Collection</strong>: Tạo/cập nhật/xóa bộ sưu tập (playlist) tài liệu cá nhân.</li>
 *   <li><strong>Recommendation</strong>: Gợi ý tài liệu liên quan dựa trên tài liệu đang xem.</li>
 * </ul>
 *
 * <p>Implementation: {@code LibraryServiceImpl}.</p>
 */
public interface LibraryService {

    /**
     * Chuyển đổi trạng thái bookmark (đánh dấu ↔ bỏ đánh dấu) cho tài liệu.
     *
     * @param user       Người dùng thực hiện bookmark.
     * @param documentId ID tài liệu cần bookmark.
     * @return {@code true} nếu sau thao tác tài liệu đang được bookmark,
     *         {@code false} nếu đã bỏ bookmark.
     */
    boolean toggleBookmark(User user, Long documentId);

    /**
     * Kiểm tra người dùng đã bookmark tài liệu này chưa.
     *
     * @param userId     ID người dùng (null = chưa đăng nhập → trả về false).
     * @param documentId ID tài liệu.
     * @return {@code true} nếu đã bookmark.
     */
    boolean isBookmarked(Long userId, Long documentId);

    /**
     * Lấy danh sách tất cả bookmark của người dùng.
     *
     * @param userId ID người dùng.
     * @return Danh sách bookmark sắp xếp theo thời gian tạo giảm dần.
     */
    List<Bookmark> bookmarks(Long userId);

    /**
     * Tạo bộ sưu tập tài liệu mới.
     *
     * @param owner       Chủ sở hữu bộ sưu tập.
     * @param name        Tên bộ sưu tập (duy nhất trong phạm vi người dùng).
     * @param description Mô tả bộ sưu tập (có thể null).
     * @return Bộ sưu tập vừa tạo.
     */
    DocumentCollection createCollection(User owner, String name, String description);

    /**
     * Cập nhật thông tin bộ sưu tập.
     *
     * @param collectionId ID bộ sưu tập cần cập nhật.
     * @param ownerId      ID chủ sở hữu (kiểm tra quyền).
     * @param name         Tên mới.
     * @param description  Mô tả mới.
     * @return Bộ sưu tập sau khi cập nhật.
     */
    DocumentCollection updateCollection(Long collectionId, Long ownerId, String name, String description);

    /**
     * Lấy danh sách bộ sưu tập của người dùng.
     *
     * @param ownerId ID người dùng.
     * @return Danh sách bộ sưu tập.
     */
    List<DocumentCollection> collections(Long ownerId);

    /**
     * Tìm bộ sưu tập theo ID và xác minh quyền sở hữu.
     * Ném exception nếu bộ sưu tập không tồn tại hoặc không thuộc về người dùng.
     *
     * @param collectionId ID bộ sưu tập.
     * @param ownerId      ID chủ sở hữu cần xác minh.
     * @return Bộ sưu tập thuộc về người dùng.
     */
    DocumentCollection findOwnedCollection(Long collectionId, Long ownerId);

    /**
     * Lấy danh sách tài liệu trong bộ sưu tập.
     *
     * @param collectionId ID bộ sưu tập.
     * @param ownerId      ID chủ sở hữu (kiểm tra quyền).
     * @return Danh sách mục trong bộ sưu tập.
     */
    List<CollectionItem> collectionItems(Long collectionId, Long ownerId);

    /**
     * Thêm tài liệu vào bộ sưu tập.
     *
     * @param collectionId ID bộ sưu tập.
     * @param documentId   ID tài liệu cần thêm.
     * @param ownerId      ID chủ sở hữu (kiểm tra quyền).
     */
    void addToCollection(Long collectionId, Long documentId, Long ownerId);

    /**
     * Xóa tài liệu khỏi bộ sưu tập.
     *
     * @param collectionId ID bộ sưu tập.
     * @param documentId   ID tài liệu cần xóa.
     * @param ownerId      ID chủ sở hữu (kiểm tra quyền).
     */
    void removeFromCollection(Long collectionId, Long documentId, Long ownerId);

    /**
     * Xóa toàn bộ bộ sưu tập và các mục bên trong.
     *
     * @param collectionId ID bộ sưu tập cần xóa.
     * @param ownerId      ID chủ sở hữu (kiểm tra quyền).
     */
    void deleteCollection(Long collectionId, Long ownerId);

    /**
     * Đếm số tài liệu trong bộ sưu tập.
     *
     * @param collectionId ID bộ sưu tập.
     * @return Số lượng tài liệu.
     */
    long collectionSize(Long collectionId);

    /**
     * Gợi ý tài liệu liên quan dựa trên tài liệu đang xem.
     * Dùng danh mục, loại tài nguyên và lịch sử xem để gợi ý phù hợp.
     *
     * @param userId          ID người dùng hiện tại (null = chưa đăng nhập).
     * @param currentDocument Tài liệu đang xem (để lấy ngữ cảnh gợi ý).
     * @param limit           Số lượng tài liệu gợi ý tối đa.
     * @return Danh sách tài liệu được gợi ý.
     */
    List<Document> recommend(Long userId, Document currentDocument, int limit);
}
