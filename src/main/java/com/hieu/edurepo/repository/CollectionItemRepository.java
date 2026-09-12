package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.CollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, Long> {
    @Query("select i from CollectionItem i join fetch i.document d left join fetch d.category "
            + "where i.collection.id = :collectionId order by i.addedAt desc")
    List<CollectionItem> findDetailedByCollectionId(@Param("collectionId") Long collectionId);
    Optional<CollectionItem> findByCollectionIdAndDocumentId(Long collectionId, Long documentId);
    long countByCollectionId(Long collectionId);
    void deleteByCollectionId(Long collectionId);
    void deleteByDocumentId(Long documentId);
}
