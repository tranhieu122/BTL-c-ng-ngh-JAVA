package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;
import java.util.Objects;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
/**
 * Kiểm thử tính bền vững của phiên hội thoại Trợ lý AI (Document Session Persistence Test).
 * Đảm bảo lịch sử tin nhắn hỏi đáp giáo trình được lưu trữ toàn vẹn và phục hồi sau khi restart ứng dụng.
 */
class DocumentSessionPersistenceTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void pendingDocumentStaysPrivateAndReappearsAfterLogoutAndLogin() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.test";
        String password = "password123";
        String title = "Pending private persistence document";
        Role submitter = roleRepository.findByName(RoleName.SUBMITTER).orElseThrow();

        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("Session Persistence");
        user.setPassword(passwordEncoder.encode(password));
        user.setRoles(Set.of(submitter));
        user = userRepository.saveAndFlush(user);

        Document document = new Document();
        document.setTitle(title);
        document.setStatus(DocumentStatus.SUBMITTED);
        document.setCreatedBy(user);
        document.setFileName("pending.pdf");
        document.setFilePath("pending.pdf");
        document.setFileType("application/pdf");
        document = documentRepository.saveAndFlush(document);

        try {
            mockMvc.perform(get("/repository"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(title))));

            HttpSession firstSession = login(email, password);
            mockMvc.perform(get("/documents").session((org.springframework.mock.web.MockHttpSession) firstSession))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(title)));

            mockMvc.perform(post("/logout").session((org.springframework.mock.web.MockHttpSession) firstSession).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?logout"));

            HttpSession secondSession = login(email, password);
            mockMvc.perform(get("/documents").session((org.springframework.mock.web.MockHttpSession) secondSession))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(title)));
        }
        finally {
            documentRepository.deleteById(document.getId());
            userRepository.deleteById(user.getId());
        }
    }

    private HttpSession login(String email, String password) throws Exception {
        return Objects.requireNonNull(
                mockMvc.perform(post("/login")
                                .param("email", email)
                                .param("password", password)
                                .with(csrf()))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/dashboard"))
                        .andReturn().getRequest().getSession(false),
                "Expected login to create a session");
    }
}
