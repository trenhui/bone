package com.bone.engine.extension.studio.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * X-2 的端到端契约验收：领域错误码必须穿过 Controller → advice 到达响应体。
 *
 * <p>用 {@code in-memory} profile：数据集随 Spring 上下文创建/销毁，无需外部库清理（D 段「不留测试数据」）。
 */
@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ExtensionApiErrorCodeTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("未知插件查部署状态 → 404 + EXT_PLUGIN_NOT_FOUND（改造前被 IllegalArgumentException 压成 400 且无领域码）")
  void unknownPlugin_deploymentState_returns404WithDomainCode() throws Exception {
    mockMvc
        .perform(get("/api/v1/extension/plugins/999999/deployment-state"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data.errorCode").value("EXT_PLUGIN_NOT_FOUND"));
  }

  @Test
  @DisplayName("重复版本号上传 → 409 + EXT_PLUGIN_VERSION_CONFLICT")
  void duplicateVersionUpload_returns409WithDomainCode() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/extension/plugins:upload")
                .file(jar())
                .param("extPointId", "1")
                .param("name", "重复版本测试插件")
                .param("className", "com.bone.test.DupVersionPlugin")
                .param("version", "7.7.7")
                .param("pluginId", "1"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            multipart("/api/v1/extension/plugins:upload")
                .file(jar())
                .param("extPointId", "1")
                .param("name", "重复版本测试插件")
                .param("className", "com.bone.test.DupVersionPlugin")
                .param("version", "7.7.7")
                .param("pluginId", "1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.data.errorCode").value("EXT_PLUGIN_VERSION_CONFLICT"));
  }

  /** 最小合法 ZIP/JAR 本地头（PK\x03\x04），满足 magic-number 校验。 */
  private static MockMultipartFile jar() {
    return new MockMultipartFile(
        "file",
        "dup-version.jar",
        "application/java-archive",
        new byte[] {
          0x50, 0x4b, 0x03, 0x04, 0x0a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x50, 0x4b, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x1c, 0x00,
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        });
  }
}
