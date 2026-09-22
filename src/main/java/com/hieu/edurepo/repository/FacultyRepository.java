package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Kho lưu trữ thông tin khoa viện trong cấu trúc tổ chức trường học.
 */
public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    List<Faculty> findByActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
