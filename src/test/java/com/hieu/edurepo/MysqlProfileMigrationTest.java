package com.hieu.edurepo;

import com.hieu.edurepo.repository.*;
import com.hieu.edurepo.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: scripts/test-profile-mysql.ps1 starts an isolated MySQL datadir, never the existing database. */
@EnabledIfSystemProperty(named = "profile.mysql.port", matches = "[0-9]+")
class MysqlProfileMigrationTest {
    @Test void migratesV3DataWithoutLossAndPersistsProfileAndDeletionAcrossRestart() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + System.getProperty("profile.mysql.port")
                + "/edurepo_profile_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh";
        Flyway.configure().dataSource(url, "root", "").target("3").load().migrate();
        String hash = new BCryptPasswordEncoder().encode("Password123");
        try (var connection = DriverManager.getConnection(url, "root", "")) {
            try (var statement = connection.prepareStatement("insert into users(id, username, email, full_name, password, enabled) values(1, 'migration@example.test', 'migration@example.test', 'Existing V3 account', ?, true)")) {
                statement.setString(1, hash); statement.executeUpdate();
            }
            connection.createStatement().executeUpdate("insert into documents(id,title,status,created_by,created_at,updated_at) values(1,'Existing V3 document','PUBLISHED',1,NOW(),NOW())");
        }
        Flyway.configure().dataSource(url, "root", "").load().migrate();
        String uploads = Path.of("target/mysql-profile-uploads").toAbsolutePath().toString();
        String[] args = {"--spring.datasource.url=" + url, "--spring.datasource.username=root", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true", "--server.port=0", "--app.upload.dir=" + uploads,
                "--app.admin.email=", "--app.admin.password=", "--app.user.email=", "--app.user.password=", "--debug=false"};
        String avatar;
        try (var first = new SpringApplicationBuilder(EduRepoApplication.class).run(args)) {
            var profiles = first.getBean(ProfileService.class);
            assertEquals("Existing V3 account", profiles.get(1L).fullName());
            assertEquals("Existing V3 document", first.getBean(DocumentRepository.class).findById(1L).orElseThrow().getTitle());
            var form = profiles.get(1L).form(); form.setFullName("MySQL profile"); form.setBio("Persistent profile"); profiles.update(1L, form);
            var png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(10,10,java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
            profiles.uploadAvatar(1L, new MockMultipartFile("avatar", "face.png", "image/png", png.toByteArray()));
            avatar = profiles.get(1L).avatarRevision();
        }
        try (var second = new SpringApplicationBuilder(EduRepoApplication.class).run(args)) {
            var profiles = second.getBean(ProfileService.class);
            assertEquals("MySQL profile", profiles.get(1L).fullName()); assertEquals(avatar, profiles.get(1L).avatarRevision());
            try (var image = second.getBean(AvatarStorageService.class).load(1L, avatar).getInputStream()) {
                assertNotNull(javax.imageio.ImageIO.read(image));
            }
            profiles.deleteAccount(1L, "Password123", "XÓA TÀI KHOẢN", true);
            assertEquals("Tài khoản đã xóa", second.getBean(DocumentRepository.class).findById(1L).orElseThrow().getCreatedBy().getFullName());
        }
        try (var third = new SpringApplicationBuilder(EduRepoApplication.class).run(args)) {
            assertTrue(third.getBean(UserRepository.class).findByEmailIgnoreCase("migration@example.test").isEmpty());
            assertNotNull(third.getBean(UserRepository.class).findById(1L).orElseThrow().getDeletedAt());
            assertTrue(third.getBean(DocumentRepository.class).existsById(1L));
        }
    }
}
