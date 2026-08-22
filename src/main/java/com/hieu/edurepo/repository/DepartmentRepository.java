package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByActiveTrueOrderByNameAsc();
    List<Department> findByFacultyIdAndActiveTrueOrderByNameAsc(Long facultyId);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByFacultyIdAndNameIgnoreCase(Long facultyId, String name);
    boolean existsByFacultyIdAndNameIgnoreCaseAndIdNot(Long facultyId, String name, Long id);
    long countByFacultyId(Long facultyId);
}
