package com.bone.metadata.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 元数据服务端真实 MySQL E2E：固化 moduleId 引用 IAM 校验异常路径 + 建模数据落库回读。 依赖本地 MySQL（BONE_DB_PASSWORD 默认
 * mysql123），与 dev 启动同源。
 */
@SpringBootTest(classes = MetadataApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetadataModuleReferenceE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("BONE_DB_PASSWORD", "mysql123"));
    registry.add("security.enabled", () -> "false");
  }

  private int countMetaEntity() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM meta_entity", Integer.class);
  }

  @Test
  void createEntity_withNonExistentModuleId_rejectedAndNoDirtyData() throws Exception {
    int before = countMetaEntity();
    String code = "e2e_bad_module_" + System.nanoTime();
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name",
                "坏模块实体",
                "code",
                code,
                "displayName",
                "坏模块实体",
                "tableName",
                "meta_e2e_bad_" + System.nanoTime(),
                "type",
                0,
                "moduleId",
                999999L));

    mockMvc
        .perform(
            post("/api/v1/metadata/entities").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("所属模块不存在")));

    int after = countMetaEntity();
    org.junit.jupiter.api.Assertions.assertEquals(before, after, "moduleId 校验失败不应写入任何实体");
  }

  @Test
  void createEntity_andField_persistedAndReadable() throws Exception {
    String code = "e2e_ok_" + System.nanoTime();
    String tableName = "meta_e2e_ok_" + System.nanoTime();
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "name",
                "正常实体",
                "code",
                code,
                "displayName",
                "正常实体",
                "tableName",
                tableName,
                "type",
                0));

    String resp =
        mockMvc
            .perform(
                post("/api/v1/metadata/entities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String entityId = objectMapper.readTree(resp).get("data").asText();

    // 落库验证：直接查 MySQL
    Integer persisted =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meta_entity WHERE code = ?", Integer.class, code);
    org.junit.jupiter.api.Assertions.assertEquals(1, persisted, "实体未真实落库");

    // 经接口回读
    mockMvc
        .perform(get("/api/v1/metadata/entities/" + entityId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.code").value(code));

    // 字段落库验证
    String fieldBody =
        objectMapper.writeValueAsString(
            Map.of(
                "name", "字段A",
                "code", "fa_" + System.nanoTime(),
                "displayName", "字段A",
                "type", "STRING",
                "required", true));
    mockMvc
        .perform(
            post("/api/v1/metadata/entities/" + entityId + "/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fieldBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));
  }
}
