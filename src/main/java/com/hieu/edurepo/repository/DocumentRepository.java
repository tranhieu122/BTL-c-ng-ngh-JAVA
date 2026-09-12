package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.dto.NamedCount;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.EducationLevel;
import com.hieu.edurepo.enums.LearningResourceType;
import com.hieu.edurepo.enums.LicenseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    @Query("select new com.hieu.edurepo.dto.LiveDocument(d.id, d.title, d.status, d.updatedAt) from Document d where d.createdBy.id = :ownerId order by d.id desc")
    List<com.hieu.edurepo.dto.LiveDocument> liveOwned(@Param("ownerId") Long ownerId);
    @Query("select new com.hieu.edurepo.dto.LiveDocument(d.id, d.title, d.status, d.updatedAt) from Document d where d.status in :statuses order by d.id desc")
    List<com.hieu.edurepo.dto.LiveDocument> liveQueue(@Param("statuses") List<DocumentStatus> statuses);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    java.util.Optional<Document> findByIdForUpdate(@Param("id") Long id);

    List<Document> findByCreatedByIdOrderByCreatedAtDesc(Long userId);
    boolean existsByCreatedById(Long userId);
    List<Document> findByStatusOrderBySubmittedAtAsc(DocumentStatus status);
    List<Document> findByStatusInOrderBySubmittedAtAsc(List<DocumentStatus> statuses);
    long countByStatus(DocumentStatus status);
    Page<Document> findByStatusAndTitleContainingIgnoreCase(
            DocumentStatus status, String keyword, Pageable pageable);
    List<Document> findTop8ByOrderByCreatedAtDesc();
    long countByDepartmentId(Long departmentId);
    long countByDepartmentFacultyId(Long facultyId);

    @Query("select new com.hieu.edurepo.dto.NamedCount(coalesce(c.name, 'Chưa phân loại'), count(d)) "
            + "from Document d left join d.category c group by c.name order by count(d) desc")
    List<NamedCount> countByCategory();

    @Query("select new com.hieu.edurepo.dto.NamedCount(coalesce(c.name, 'Chưa phân loại'), count(d)) "
            + "from Document d left join d.category c where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "group by c.name order by count(d) desc")
    List<NamedCount> countPublishedByCategory();

    @Query("select new com.hieu.edurepo.dto.NamedCount(coalesce(f.name, 'Chưa gán khoa'), count(d)) "
            + "from Document d left join d.department dep left join dep.faculty f group by f.name order by count(d) desc")
    List<NamedCount> countByFaculty();

    @Query("select d from Document d left join d.category c where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%'))) "
            + "and (:categoryId is null or c.id = :categoryId) "
            + "and (:resourceType is null or d.learningResourceType = :resourceType) "
            + "and (:educationLevel is null or d.educationLevel = :educationLevel) "
            + "and (:licenseType is null or d.licenseType = :licenseType) "
            + "and (:languageCode = '' or lower(coalesce(d.languageCode, 'vi')) = lower(:languageCode))")
    Page<Document> searchPublishedAdvanced(@Param("keyword") String keyword,
                                            @Param("categoryId") Long categoryId,
                                            @Param("resourceType") LearningResourceType resourceType,
                                            @Param("educationLevel") EducationLevel educationLevel,
                                            @Param("licenseType") LicenseType licenseType,
                                            @Param("languageCode") String languageCode,
                                            Pageable pageable);

    @Query("select d from Document d where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "and d.id <> :documentId and d.category.id = :categoryId "
            + "order by (d.viewCount + d.downloadCount * 2) desc, d.publishedAt desc")
    List<Document> findRelatedPublished(@Param("documentId") Long documentId,
                                         @Param("categoryId") Long categoryId,
                                         Pageable pageable);

    @Query("select distinct d from Document d where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "and d.category.id in :categoryIds and (:excludeId is null or d.id <> :excludeId) "
            + "order by (d.viewCount + d.downloadCount * 2) desc, d.publishedAt desc")
    List<Document> findRecommendedByCategories(@Param("categoryIds") List<Long> categoryIds,
                                                @Param("excludeId") Long excludeId,
                                                Pageable pageable);

    List<Document> findTop8ByStatusOrderByDownloadCountDescViewCountDescPublishedAtDesc(DocumentStatus status);

    @Query("select coalesce(sum(d.viewCount), 0) from Document d")
    long sumViewCount();

    @Query("select coalesce(sum(d.downloadCount), 0) from Document d")
    long sumDownloadCount();

    @Modifying(clearAutomatically = true)
    @Query("update Document d set d.viewCount = d.viewCount + 1 where d.id = :id and d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED")
    int incrementViewCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("update Document d set d.downloadCount = d.downloadCount + 1 where d.id = :id and d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED")
    int incrementDownloadCount(@Param("id") Long id);

    @Query("select d from Document d left join d.category c left join d.department dep "
            + "left join dep.faculty f where d.createdBy.id = :ownerId "
            + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%'))) "
            + "and (:status is null or d.status = :status) "
            + "and (:categoryId is null or c.id = :categoryId) "
            + "and (:facultyId is null or f.id = :facultyId) "
            + "and (:departmentId is null or dep.id = :departmentId)")
    Page<Document> searchByOwner(@Param("ownerId") Long ownerId, @Param("keyword") String keyword,
                                 @Param("status") DocumentStatus status, @Param("categoryId") Long categoryId,
                                 @Param("facultyId") Long facultyId, @Param("departmentId") Long departmentId,
                                 Pageable pageable);
}
