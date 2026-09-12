package com.hieu.edurepo;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceRestartIntegrationTest {

    @Test
    void userDocumentRecordAndUploadedFileSurviveApplicationRestart() throws Exception {
        Path temporaryDirectory = Path.of("target", "test-work", "restart-" + UUID.randomUUID())
                .toAbsolutePath();
        Files.createDirectories(temporaryDirectory);
        Path database = temporaryDirectory.resolve("database/edurepo");
        Path uploads = temporaryDirectory.resolve("uploads");
        String email = "restart@example.test";
        String storedFile;
        String avatarKey;

        try (ConfigurableApplicationContext first = start(database, uploads)) {
            Role submitter = first.getBean(RoleRepository.class).findByName(RoleName.SUBMITTER).orElseThrow();
            User user = new User();
            user.setUsername(email);
            user.setEmail(email);
            user.setFullName("Restart Test");
            user.setPassword("encoded-test-password");
            user.setRoles(Set.of(submitter));
            user = first.getBean(UserRepository.class).saveAndFlush(user);

            var profile = new com.hieu.edurepo.dto.ProfileForm();
            profile.setFullName("Persistent profile"); profile.setAffiliation("Persistent school");
            first.getBean(com.hieu.edurepo.service.ProfileService.class).update(user.getId(), profile);
            var image = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(12, 12, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", image);
            first.getBean(com.hieu.edurepo.service.ProfileService.class).uploadAvatar(user.getId(),
                    new MockMultipartFile("avatar", "restart.png", "image/png", image.toByteArray()));
            avatarKey = first.getBean(com.hieu.edurepo.service.ProfileService.class).get(user.getId()).avatarRevision();

            storedFile = first.getBean(FileStorageService.class).store(new MockMultipartFile(
                    "file", "restart.pdf", "application/pdf", "%PDF-1.7 durable".getBytes()));
            Document document = new Document();
            document.setTitle("Durable restart document");
            document.setStatus(DocumentStatus.SUBMITTED);
            document.setCreatedBy(user);
            document.setFileName("restart.pdf");
            document.setFilePath(storedFile);
            document.setFileType("application/pdf");
            document.setFileSize(16L);
            first.getBean(DocumentRepository.class).saveAndFlush(document);
        }

        try (ConfigurableApplicationContext second = start(database, uploads)) {
            User restoredUser = second.getBean(UserRepository.class).findByEmailIgnoreCase(email).orElseThrow();
            Document restoredDocument = second.getBean(DocumentRepository.class)
                    .findByCreatedByIdOrderByCreatedAtDesc(restoredUser.getId()).stream().findFirst().orElseThrow();

            assertEquals("Durable restart document", restoredDocument.getTitle());
            assertEquals(DocumentStatus.SUBMITTED, restoredDocument.getStatus());
            assertEquals(storedFile, restoredDocument.getFilePath());
            assertTrue(Files.isRegularFile(uploads.resolve(storedFile)));
            assertEquals("Persistent profile", restoredUser.getFullName());
            assertEquals("Persistent school", restoredUser.getAffiliation());
            assertEquals(avatarKey, restoredUser.getAvatarKey());
            assertTrue(second.getBean(com.hieu.edurepo.service.AvatarStorageService.class).load(restoredUser.getId(), avatarKey).exists());
        }
    }

    private ConfigurableApplicationContext start(Path database, Path uploads) {
        String jdbcUrl = "jdbc:h2:file:" + database.toAbsolutePath().toString().replace('\\', '/')
                + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE";
        return new SpringApplicationBuilder(EduRepoApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.flyway.enabled=false",
                        "--server.port=0",
                        "--app.upload.dir=" + uploads.toAbsolutePath(),
                        "--app.admin.email=",
                        "--app.admin.password=",
                        "--app.user.email=",
                        "--app.user.password=",
                        "--logging.level.root=WARN");
    }
}
