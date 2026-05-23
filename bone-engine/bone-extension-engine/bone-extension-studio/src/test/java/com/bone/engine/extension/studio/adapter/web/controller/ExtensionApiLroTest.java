package com.bone.engine.extension.studio.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
@TestPropertySource(properties = "bone.extension.studio.lro.deploy-sync-by-default=false")
class ExtensionApiLroTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName(":deploy 异步 202 + GET operations 轮询至 done")
    void deploy_async_pollUntilDone() throws Exception {
        MvcResult accepted =
                mockMvc.perform(post("/api/v1/extension/plugins/1:deploy"))
                        .andExpect(status().isAccepted())
                        .andExpect(header().exists("Location"))
                        .andExpect(jsonPath("$.data.operationId").isString())
                        .andReturn();
        String location = accepted.getResponse().getHeader("Location");
        String operationId = location.substring(location.lastIndexOf('/') + 1);

        for (int i = 0; i < 50; i++) {
            MvcResult poll = mockMvc.perform(get("/api/v1/extension/operations/" + operationId))
                    .andExpect(status().isOk())
                    .andReturn();
            if (poll.getResponse().getContentAsString().contains("\"done\":true")) {
                mockMvc.perform(get("/api/v1/extension/operations/" + operationId))
                        .andExpect(jsonPath("$.data.done").value(true))
                        .andExpect(jsonPath("$.data.progress").value(100))
                        .andExpect(jsonPath("$.data.result.id").value(1));
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("LRO deploy did not complete in time");
    }
}
