package com.hieu.edurepo.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserModuleAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotOpenUploadPage() throws Exception {
        mockMvc.perform(get("/documents/new"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUBMITTER")
    void submitterCanStillOpenUploadPage() throws Exception {
        mockMvc.perform(get("/documents/new"))
                .andExpect(status().isOk());
    }
}
