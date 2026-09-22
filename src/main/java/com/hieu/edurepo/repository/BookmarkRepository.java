package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ dữ liệu đánh dấu tài liệu yêu thích (Bookmark) của người dùng.
 */
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByUserIdAndDocumentId(Long userId, Long documentId);
    boolean existsByUserIdAndDocumentId(Long userId, Long documentId);
    @Query("select b from Bookmark b join fetch b.document d left join fetch d.category "
            + "where b.user.id = :userId order by b.createdAt desc")
    List<Bookmark> findDetailedByUserId(@Param("userId") Long userId);
    void deleteByDocumentId(Long documentId);
    void deleteByUserId(Long userId);
}
