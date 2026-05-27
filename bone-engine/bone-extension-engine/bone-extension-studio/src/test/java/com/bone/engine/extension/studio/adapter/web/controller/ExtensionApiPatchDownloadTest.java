package com.bone.engine.extension.studio.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ExtensionApiPatchDownloadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("PATCH /points/{id} 仅更新 description")
    void patchPoint_partialUpdate() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/extension/points/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"PATCH 局部更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.description").value("PATCH 局部更新"));
    }

    @Test
    @DisplayName("PATCH /plugins/{id} 仅更新 priority")
    void patchPlugin_partialUpdate() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/extension/plugins/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"priority\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.priority").value(42));
    }

    @Test
    @DisplayName("GET /plugins/{id}/versions/{ver}:download 返回制品流")
    void downloadPluginVersion_returnsAttachment() throws Exception {
        MockMultipartFile jar =
                new MockMultipartFile(
                        "file",
                        "dl-test.jar",
                        "application/java-archive",
                        minimalJarBytes());

        mockMvc.perform(
                        multipart("/api/v1/extension/plugins:upload")
                                .file(jar)
                                .param("pluginId", "1")
                                .param("name", "默认促销实现")
                                .param("className", "com.bone.example.DefaultPricingExtension")
                                .param("version", "dl-1.0"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/extension/plugins/1/versions/dl-1.0:download"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    private static byte[] minimalJarBytes() {
        return new byte[] {
            0x50, 0x4b, 0x03, 0x04, 0x0a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x50, 0x4b, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x1c, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
    }
}
