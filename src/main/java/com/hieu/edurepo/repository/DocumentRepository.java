package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.dto.NamedCount;
import com.hieu.edurepo.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
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

    @Query("select new com.hieu.edurepo.dto.NamedCount(coalesce(f.name, 'Chưa gán khoa'), count(d)) "
            + "from Document d left join d.department dep left join dep.faculty f group by f.name order by count(d) desc")
    List<NamedCount> countByFaculty();

    @Query("select d from Document d where d.createdBy.id = :ownerId "
            + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%'))) "
            + "and (:status is null or d.status = :status) "
            + "and (:categoryId is null or d.category.id = :categoryId) "
            + "and (:facultyId is null or d.department.faculty.id = :facultyId) "
            + "and (:departmentId is null or d.department.id = :departmentId)")
    Page<Document> searchByOwner(@Param("ownerId") Long ownerId, @Param("keyword") String keyword,
                                 @Param("status") DocumentStatus status, @Param("categoryId") Long categoryId,
                                 @Param("facultyId") Long facultyId, @Param("departmentId") Long departmentId,
                                 Pageable pageable);
}
