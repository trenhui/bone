package com.bone.engine.extension.studio.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ExtensionApiUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /plugins:upload 新建插件并登记版本")
    void uploadPlugin_registersVersion() throws Exception {
        MockMultipartFile jar =
                new MockMultipartFile(
                        "file",
                        "demo-plugin.jar",
                        "application/java-archive",
                        minimalJarBytes());

        mockMvc.perform(
                        multipart("/api/v1/extension/plugins:upload")
                                .file(jar)
                                .param("extPointId", "1")
                                .param("name", "上传测试插件")
                                .param("className", "com.bone.test.UploadedPlugin")
                                .param("version", "9.9.9"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("上传测试插件"))
                .andExpect(jsonPath("$.data.className").value("com.bone.test.UploadedPlugin"));
    }

    /** 最小合法 ZIP/JAR 本地头（PK\\x03\\x04），满足 magic-number 校验。 */
    private static byte[] minimalJarBytes() {
        return new byte[] {
            0x50, 0x4b, 0x03, 0x04, 0x0a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x50, 0x4b, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x1c, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
    }
}
