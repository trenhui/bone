package com.bone.engine.extension.studio.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 5a G1/G4/G5：应用归属（app_id）全链 + 租户码校验 API 冒烟（in-memory）。 */
@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ExtensionAppScopeApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ExtensionRepository extensionRepository;
  @Autowired private ObjectMapper objectMapper;

  private Extension seed(long appId, String tenantCode) {
    Extension plugin =
        Extension.create(1L, "应用归属测试插件-" + appId, "5a G1", "com.bone.test.AppScopeImpl" + appId);
    plugin.setAppId(appId);
    plugin.setTenantCode(tenantCode);
    return extensionRepository.save(plugin);
  }

  @Test
  @DisplayName("按 appId 过滤插件列表（应用扩展视图数据源）")
  void listByAppId() throws Exception {
    Extension mine = seed(9001L, "*");
    seed(9002L, "*");
    mockMvc
        .perform(get("/api/v1/extension/plugins").param("appId", "9001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data[?(@.id == " + mine.getId() + " && @.appId == 9001)].name").exists());
  }

  @Test
  @DisplayName("市场安装携带 appId 落库（免上传实例化归属）")
  void marketplaceInstallWritesAppId() throws Exception {
    // 先登记市场条目声明的扩展点接口，安装才能解析 extPointId
    String pointBody =
        "{\"name\":\"促销扩展点\",\"interfaceName\":\"com.bone.example.PromotionExt\",\"enabled\":true}";
    String pointResponse =
        mockMvc
            .perform(
                post("/api/v1/extension/points")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(pointBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Object pointId =
        objectMapper.readTree(pointResponse).at("/data/id").asLong() > 0
            ? objectMapper.readTree(pointResponse).at("/data/id").asLong()
            : null;

    String body =
        "{\"appId\": 77001" + (pointId != null ? ", \"extPointId\": " + pointId : "") + "}";
    String response =
        mockMvc
            .perform(
                post("/api/v1/extension/marketplace/discount.percentage-promo:install")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appId").value(77001))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long pluginId = objectMapper.readTree(response).at("/data/pluginId").asLong();
    Extension installed = extensionRepository.findById(pluginId);
    assertThat(installed).isNotNull();
    assertThat(installed.getAppId()).isEqualTo(77001L);
  }

  @Test
  @DisplayName("PATCH appId 可更新归属；tenantCode=DEFAULT 拒绝")
  void patchAppId_andTenantCodeGuard() throws Exception {
    Extension plugin = seed(88001L, "*");
    // appId 更新
    mockMvc
        .perform(
            patch("/api/v1/extension/plugins/" + plugin.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appId\": 88002}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.appId").value(88002));
    // 遗留 DEFAULT 拒绝
    mockMvc
        .perform(
            patch("/api/v1/extension/plugins/" + plugin.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantCode\": \"DEFAULT\"}"))
        .andExpect(status().isBadRequest());
  }
}
