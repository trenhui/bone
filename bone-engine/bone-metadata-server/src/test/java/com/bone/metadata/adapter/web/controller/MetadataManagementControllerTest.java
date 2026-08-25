package com.bone.metadata.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.metadata.MetadataApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 元数据服务端 Controller 集成测试（真实本地 MySQL，profile=test）。 覆盖实体 CRUD、字段创建读回、关系、发布主链路的行为契约。 */
@SpringBootTest(classes = MetadataApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetadataManagementControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("BONE_DB_PASSWORD", "mysql123"));
    registry.add("security.enabled", () -> "false");
  }

  private String createEntity() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name",
                "E2E实体",
                "code",
                "e2e_entity_" + System.nanoTime(),
                "displayName",
                "E2E实体",
                "tableName",
                "meta_e2e_" + System.nanoTime(),
                "type",
                0));
    String response =
        mockMvc
            .perform(
                post("/api/v1/metadata/entities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("data").asText();
  }

  @Test
  void entityCrud_returnsApiResponseContract() throws Exception {
    String id = createEntity();

    mockMvc
        .perform(get("/api/v1/metadata/entities/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(Long.valueOf(id)));

    mockMvc
        .perform(
            put("/api/v1/metadata/entities/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "name",
                            "E2E实体改",
                            "code",
                            "e2e_entity_x_" + System.nanoTime(),
                            "displayName",
                            "E2E实体改",
                            "tableName",
                            "meta_e2e_x_" + System.nanoTime()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(get("/api/v1/metadata/entities").param("page", "1").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.records").isArray());

    mockMvc
        .perform(delete("/api/v1/metadata/entities/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void fieldCreateAndReadBack_underEntity() throws Exception {
    String entityId = createEntity();
    String fieldBody =
        objectMapper.writeValueAsString(
            Map.of(
                "name", "字段1",
                "code", "f_" + System.nanoTime(),
                "displayName", "字段1",
                "type", "STRING",
                "required", true));

    String fieldResp =
        mockMvc
            .perform(
                post("/api/v1/metadata/entities/" + entityId + "/fields")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(fieldBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String fieldId = objectMapper.readTree(fieldResp).get("data").asText();

    mockMvc
        .perform(get("/api/v1/metadata/entities/" + entityId + "/fields/" + fieldId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.code").exists());
  }

  @Test
  void relationshipEndpoint_reachable() throws Exception {
    mockMvc
        .perform(get("/api/v1/metadata/relationships").param("page", "1").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void publishEntity_returnsOk() throws Exception {
    String entityId = createEntity();
    mockMvc
        .perform(post("/api/v1/metadata/entities/" + entityId + "/publish"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
