package com.hieu.edurepo.security;

import com.hieu.edurepo.observability.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,info,prometheus",
        "management.prometheus.metrics.export.enabled=true",
        "management.endpoint.health.show-details=when-authorized",
        "management.endpoint.health.show-components=when-authorized",
        "management.endpoint.health.roles=ADMIN",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.liveness.include=livenessState",
        "management.endpoint.health.group.liveness.show-details=never",
        "management.endpoint.health.group.liveness.show-components=never",
        "management.endpoint.health.group.readiness.include=readinessState,db,diskSpace",
        "management.endpoint.health.group.readiness.show-details=never",
        "management.endpoint.health.group.readiness.show-components=never"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
/**
 * Kiểm thử an ninh phân quyền các cổng giám sát Spring Boot Actuator.
 * Đảm bảo chỉ người dùng có quyền ROLE_ADMIN mới được truy cập các cổng giám sát nhạy cảm.
 */
class ActuatorSecurityIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void publicHealthIsMinimalAndGetsServerGeneratedRequestId() throws Exception {
        mvc.perform(get("/actuator/health").header(RequestCorrelationFilter.HEADER, "client-controlled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jdbc:"))))
                .andExpect(header().string(RequestCorrelationFilter.HEADER,
                        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                .andExpect(header().string(RequestCorrelationFilter.HEADER,
                        org.hamcrest.Matchers.not("client-controlled")));

        for (String probe : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            mvc.perform(get(probe))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }
    }

    @Test
    void anonymousCannotAccessManagementEndpoints() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/actuator/health/db"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotAccessManagementEndpoints() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessExposedManagementEndpoints() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
