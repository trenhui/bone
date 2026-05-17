package com.bone.engine.extension.studio.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ExtensionManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void overview_returnsApiResponseWithTraceHeader() throws Exception {
        mockMvc.perform(get("/api/v1/extension/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void listPoints_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/extension/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void executionLogs_defaultCursorPagination() throws Exception {
        mockMvc.perform(get("/api/v1/extension/execution-logs").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void auditLogs_afterDeploy_containsEntry() throws Exception {
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/extension/plugins/1:deploy"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/extension/audit-logs").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].action").value("plugin.deploy"));
    }
}
