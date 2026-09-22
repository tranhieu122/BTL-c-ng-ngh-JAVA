package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.DocumentCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ các bộ sưu tập tài liệu cá nhân của người dùng.
 */
public interface DocumentCollectionRepository extends JpaRepository<DocumentCollection, Long> {
    List<DocumentCollection> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    Optional<DocumentCollection> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(Long ownerId, String name, Long id);
}
