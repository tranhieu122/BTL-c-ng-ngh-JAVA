package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class PublicPagesRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void publicPagesRenderWithPublishedDocument() throws Exception {
        Document document = new Document();
        document.setTitle("Giáo trình kiểm thử giao diện");
        document.setDescription("Tài liệu mẫu dùng để xác nhận các template Thymeleaf được render thành công.");
        document.setAuthorName("EduRepo QA");
        document.setFileName("giao-trinh-kiem-thu.pdf");
        document.setFileType("application/pdf");
        document.setFileSize(1_572_864L);
        document.setStatus(DocumentStatus.PUBLISHED);
        document.setPublishedAt(LocalDateTime.now());
        Document savedDocument = documentRepository.save(document);

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-login-form")));

        mockMvc.perform(get("/repository"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/repository"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Giáo trình kiểm thử giao diện")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-document-card")));

        mockMvc.perform(get("/repository/{id}", savedDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("public/document-detail"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Giáo trình kiểm thử giao diện")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-share-link")));
    }

    @Test
    void frontendEntryPointsAndModulesAreServed() throws Exception {
        String[] assets = {
                "/css/style.css",
                "/css/foundation.css",
                "/css/app-shell.css",
                "/css/responsive.css",
                "/css/components/header.css",
                "/css/components/footer.css",
                "/css/pages/repository.css",
                "/css/pages/document-detail.css",
                "/css/pages/login.css",
                "/js/main.js",
                "/js/modules/core.js",
                "/js/modules/navigation.js",
                "/js/modules/catalog.js",
                "/js/modules/auth.js",
                "/js/modules/document-actions.js"
        };

        for (String asset : assets) {
            mockMvc.perform(get(asset))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/css/style.css"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("@import url(\"./foundation.css\")")));

        mockMvc.perform(get("/js/main.js"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("./modules/core.js")));
    }
}
