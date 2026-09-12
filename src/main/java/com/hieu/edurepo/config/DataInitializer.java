package com.hieu.edurepo.config;

import com.hieu.edurepo.entity.Department;
import com.hieu.edurepo.entity.Faculty;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.DepartmentRepository;
import com.hieu.edurepo.repository.FacultyRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String userEmail;
    private final String userPassword;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           FacultyRepository facultyRepository,
                           DepartmentRepository departmentRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.email:}") String adminEmail,
                           @Value("${app.admin.password:}") String adminPassword,
                           @Value("${app.user.email:}") String userEmail,
                           @Value("${app.user.password:}") String userPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.userEmail = userEmail;
        this.userPassword = userPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Arrays.stream(RoleName.values()).forEach(roleName ->
                roleRepository.findByName(roleName)
                        .orElseGet(() -> roleRepository.save(new Role(roleName))));

        // Starter organization belongs only in an empty repository. Later edits are
        // administrator-owned; replaying name-based seeds can recreate renamed units.
        if (facultyRepository.count() == 0 && departmentRepository.count() == 0) {
            seedOrganizationData();
        }

        seedAccountIfAbsent(adminEmail, adminPassword, "Quản trị viên",
                new HashSet<>(roleRepository.findAll()));

        roleRepository.findByName(RoleName.USER).ifPresent(userRole ->
                seedAccountIfAbsent(userEmail, userPassword, "Người dùng demo", Set.of(userRole)));
    }

    /**
     * Local starter data for the submission form. Names are deliberately stable so
     * it is safe to run on every restart without duplicating faculties or departments.
     */
    private void seedOrganizationData() {
        Map<String, String[]> organization = new LinkedHashMap<>();
        organization.put("Khoa Công nghệ Thông tin", new String[]{
                "Kỹ thuật Phần mềm", "Khoa học Máy tính", "Hệ thống Thông tin", "Mạng máy tính và Truyền thông"});
        organization.put("Khoa Điện - Điện tử", new String[]{
                "Kỹ thuật Điện", "Kỹ thuật Điện tử", "Tự động hoá"});
        organization.put("Khoa Cơ khí", new String[]{
                "Kỹ thuật Cơ khí", "Cơ điện tử", "Kỹ thuật Ô tô"});
        organization.put("Khoa Kinh tế", new String[]{
                "Quản trị Kinh doanh", "Kế toán", "Tài chính - Ngân hàng", "Marketing"});
        organization.put("Khoa Ngoại ngữ", new String[]{
                "Tiếng Anh", "Tiếng Trung Quốc", "Ngôn ngữ học ứng dụng"});
        organization.put("Khoa Xây dựng", new String[]{
                "Kỹ thuật Xây dựng", "Kiến trúc", "Kinh tế Xây dựng"});
        organization.put("Khoa Khoa học Cơ bản", new String[]{
                "Toán học", "Vật lý", "Hoá học"});
        organization.put("Khoa Du lịch và Dịch vụ", new String[]{
                "Quản trị Du lịch", "Quản trị Khách sạn", "Quản trị Nhà hàng"});

        organization.forEach((facultyName, departmentNames) -> {
            Faculty faculty = findOrCreateFaculty(facultyName);
            for (String departmentName : departmentNames) {
                findOrCreateDepartment(faculty, departmentName);
            }
        });
    }

    private Faculty findOrCreateFaculty(String name) {
        return facultyRepository.findAll().stream()
                .filter(item -> name.equalsIgnoreCase(item.getName().trim()))
                .findFirst()
                .orElseGet(() -> {
                    Faculty faculty = new Faculty();
                    faculty.setName(name);
                    faculty.setDescription("Đơn vị đào tạo " + name.toLowerCase(Locale.ROOT));
                    faculty.setActive(true);
                    return facultyRepository.save(faculty);
                });
    }

    private void findOrCreateDepartment(Faculty faculty, String name) {
        boolean exists = departmentRepository.findAll().stream()
                .anyMatch(item -> name.equalsIgnoreCase(item.getName().trim())
                        && item.getFaculty() != null
                        && item.getFaculty().getId().equals(faculty.getId()));
        if (exists) {
            return;
        }
        Department department = new Department();
        department.setName(name);
        department.setDescription("Bộ môn thuộc " + faculty.getName());
        department.setFaculty(faculty);
        department.setActive(true);
        departmentRepository.save(department);
    }

    private void seedAccountIfAbsent(String email, String password, String fullName, Set<Role> roles) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return;
        }
        String bootstrapKey;
        try {
            bootstrapKey = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(normalizedEmail.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }

        // The demo account uses its email as username. Existing databases can contain
        // either key with a different casing or surrounding whitespace, so resolve by
        // both values before creating anything.
        List<User> matchingUsers = userRepository.findAll().stream()
                .filter(candidate -> normalizedEmail.equals(normalizeEmail(candidate.getEmail()))
                        || normalizedEmail.equals(normalizeEmail(candidate.getUsername()))
                        || bootstrapKey.equals(candidate.getBootstrapKey()))
                .toList();
        // Existing accounts are owned by the administrator after first startup.
        // Never overwrite their password, roles, profile, or enabled state here.
        if (!matchingUsers.isEmpty()) {
            // Keep a bootstrap marker after anonymization so restart cannot recreate a deleted demo login.
            matchingUsers.stream().filter(user -> user.getBootstrapKey() == null).forEach(user -> {
                user.setBootstrapKey(bootstrapKey); userRepository.save(user);
            });
            return;
        }
        User user = new User();
        user.setUsername(normalizedEmail);
        user.setBootstrapKey(bootstrapKey);
        user.setFullName(fullName);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setRoles(roles);
        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
