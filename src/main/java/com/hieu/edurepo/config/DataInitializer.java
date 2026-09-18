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
        // Role là dữ liệu nền tảng cho Spring Security, nên luôn đảm bảo đủ enum RoleName trong database.
        Arrays.stream(RoleName.values()).forEach(roleName ->
                roleRepository.findByName(roleName)
                        .orElseGet(() -> roleRepository.save(new Role(roleName))));

        // Dữ liệu khoa/bộ môn mẫu chỉ seed khi hệ thống hoàn toàn trống.
        // Sau khi admin chỉnh cơ cấu tổ chức, initializer không tự tạo lại tên cũ ở lần restart.
        if (facultyRepository.count() == 0 && departmentRepository.count() == 0) {
            seedOrganizationData();
        }

        seedAccountIfAbsent(adminEmail, adminPassword, "Quản trị viên",
                new HashSet<>(roleRepository.findAll()));

        roleRepository.findByName(RoleName.USER).ifPresent(userRole ->
                seedAccountIfAbsent(userEmail, userPassword, "Người dùng demo", Set.of(userRole)));
    }

    /**
     * Dữ liệu mẫu ban đầu cho form nộp tài liệu.
     * Hàm này chỉ được gọi khi chưa có khoa và chưa có bộ môn nào trong hệ thống.
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
        // bootstrapKey là dấu vết ổn định theo email cấu hình để nhận diện tài khoản seed qua các lần restart.
        // Nó giúp tránh tạo lại tài khoản demo nếu email/username cũ đã được admin chỉnh.
        String bootstrapKey;
        try {
            bootstrapKey = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(normalizedEmail.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }

        // Tài khoản seed dùng email làm username. Database cũ có thể còn email/username khác hoa thường,
        // nên tìm bằng cả email, username và bootstrapKey trước khi quyết định tạo mới.
        List<User> matchingUsers = userRepository.findAll().stream()
                .filter(candidate -> normalizedEmail.equals(normalizeEmail(candidate.getEmail()))
                        || normalizedEmail.equals(normalizeEmail(candidate.getUsername()))
                        || bootstrapKey.equals(candidate.getBootstrapKey()))
                .toList();
        // Tài khoản đã tồn tại thuộc quyền quản trị của admin sau lần khởi động đầu.
        // Không tự ghi đè mật khẩu, vai trò, hồ sơ hoặc trạng thái kích hoạt tại đây.
        if (!matchingUsers.isEmpty()) {
            // Bổ sung bootstrapKey cho dữ liệu cũ để các lần restart sau vẫn nhận ra tài khoản seed.
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
