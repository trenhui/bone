package com.bone.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * 订单 / 支付场景的「前端 → 后端」全链路集成测试（HTTP 层，真实本地 MySQL，profile=test）。
 *
 * <p><b>覆盖链路</b>：建模实体（RUNTIME 交付模式）→ 建字段 → 发布（自动建物理表 align）→ 运行时动态 CRUD（创建 / 校验失败 400 / 唯一冲突 409 /
 * 列表分页）。完全模拟前端 {@code RuntimeDataManagement} 页面的调用序列，验证模式 B「建模即生效」与本次生产就绪优化的端到端行为。
 *
 * <p><b>真源</b>：实体字段直接取自 {@code bone-blueprint} 的 {@code Order} / {@code Payment} 聚合根。
 *
 * <p><b>运行要求</b>：本地 MySQL 可达（密码取自 {@code BONE_DB_PASSWORD}，默认 mysql123）。CI 中由集成测试阶段执行； 本类不依赖 H2，因
 * {@code JdbcPhysicalStructureGatewayAdapter} 使用 MySQL {@code information_schema}。
 */
@SpringBootTest(classes = MetadataApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("订单/支付场景 · 元数据全链路 E2E（HTTP/MockMvc，蓝图真源）")
class OrderPaymentMetadataE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** 本测试类创建的实体 id / 物理表名——@AfterEach 强制清理，杜绝污染 dev 库（历次泄漏的教训）。 */
  private final java.util.List<String> createdEntityIds = new java.util.ArrayList<>();

  private final java.util.List<String> createdTableNames = new java.util.ArrayList<>();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("BONE_DB_PASSWORD", "mysql123"));
    registry.add("security.enabled", () -> "false");
  }

  @BeforeEach
  void setupTenant() {
    // 安全关闭时无 JWT，模拟 JWT 过滤器写入的租户上下文
    TenantContext.setTenantId(1L);
  }

  @AfterEach
  void clearTenantAndCleanup() {
    // 自清理：先 DROP 物理表（软删实体后表仍残留——历次运行累积 bp_*_sim_* 残留表的根因），
    // 再走 API 软删实体。均为尽力而为，失败不遮蔽原测试结果。
    for (String table : createdTableNames) {
      try {
        jdbcTemplate.execute("DROP TABLE IF EXISTS `" + table + "`");
      } catch (RuntimeException ignored) {
        // 物理表可能未创建（发布前失败的用例）
      }
    }
    for (String entityId : createdEntityIds) {
      try {
        mockMvc.perform(delete("/api/v1/metadata/entities/" + entityId)).andReturn();
      } catch (Exception ignored) {
        // 尽力清理
      }
    }
    createdEntityIds.clear();
    createdTableNames.clear();
    TenantContext.clear();
  }

  @Test
  @DisplayName("端到端：建模订单实体→发布→运行时创建/校验/唯一冲突/分页")
  void orderEntity_fullRuntimeFlow() throws Exception {
    String suffix = String.valueOf(System.nanoTime());
    String entityCode = "bp_order_sim_" + suffix;
    String tableName = "bp_order_sim_" + suffix;

    // 1) 建模订单实体（RUNTIME 交付模式，deliveryMode=1）
    String entityId = createEntity(entityCode, tableName, 1);

    // 2) 建字段（蓝图 Order 字段）
    createField(entityId, "customer_id", "LONG", true, false);
    createField(entityId, "total_amount", "DECIMAL", true, false);
    createField(entityId, "status", "STRING", true, false);

    // 3) 发布 → 自动建物理表
    mockMvc
        .perform(post("/api/v1/metadata/entities/" + entityId + "/publish"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // 4) 运行时创建订单（模拟前端 RuntimeDataManagement 提交）
    String createBody =
        objectMapper.writeValueAsString(
            Map.of("customer_id", 1001, "total_amount", 199.99, "status", "CREATED"));
    mockMvc
        .perform(
            post("/api/v1/runtime/entities/" + entityCode + "/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));

    // 5) 校验失败：缺失必填 total_amount → 400 META_RUNTIME_VALIDATION_FAILED
    String missingBody =
        objectMapper.writeValueAsString(Map.of("customer_id", 1001, "status", "CREATED"));
    mockMvc
        .perform(
            post("/api/v1/runtime/entities/" + entityCode + "/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.data.errorCode").value("META_RUNTIME_VALIDATION_FAILED"));

    // 6) 列表分页（模拟前端表格加载）
    mockMvc
        .perform(
            get("/api/v1/runtime/entities/" + entityCode + "/records")
                .param("page", "1")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.records").isArray());
  }

  @Test
  @DisplayName("端到端：支付实体唯一键 channel_trade_no → 重复创建 409（支付幂等去重）")
  void paymentEntity_duplicateUniqueConflict() throws Exception {
    String suffix = String.valueOf(System.nanoTime());
    String entityCode = "bp_pay_sim_" + suffix;
    String tableName = "bp_pay_sim_" + suffix;

    String entityId = createEntity(entityCode, tableName, 1);
    createField(entityId, "order_id", "LONG", true, false);
    createField(entityId, "customer_id", "LONG", true, false);
    createField(entityId, "amount", "DECIMAL", true, false);
    createField(entityId, "channel", "STRING", true, false);
    createField(entityId, "status", "STRING", true, false);
    createField(entityId, "channel_trade_no", "STRING", false, true);

    mockMvc
        .perform(post("/api/v1/metadata/entities/" + entityId + "/publish"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    String payBody =
        objectMapper.writeValueAsString(
            Map.of(
                "order_id",
                1,
                "customer_id",
                1001,
                "amount",
                88.0,
                "channel",
                "WECHAT",
                "status",
                "PENDING",
                "channel_trade_no",
                "WX-E2E-1"));

    mockMvc
        .perform(
            post("/api/v1/runtime/entities/" + entityCode + "/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payBody))
        .andExpect(status().isCreated());

    // 同渠道流水号重复 → 409
    mockMvc
        .perform(
            post("/api/v1/runtime/entities/" + entityCode + "/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.data.errorCode").value("META_RUNTIME_DUPLICATE"));
  }

  @Test
  @DisplayName("端到端：发布期类型漂移拦截（模型 DECIMAL vs 物理 VARCHAR → 409，运行期 500 前移）")
  void publish_detectsTypeDrift_returns409() throws Exception {
    String suffix = String.valueOf(System.nanoTime());
    String entityCode = "bp_drift_sim_" + suffix;
    String tableName = "bp_drift_sim_" + suffix;

    String entityId = createEntity(entityCode, tableName, 1);
    createField(entityId, "amount", "DECIMAL", true, false);

    // 首次发布：物理表不存在，validateForPublish 放行，align 建表（amount DECIMAL(20,6)）
    mockMvc
        .perform(post("/api/v1/metadata/entities/" + entityId + "/publish"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // 模拟人工/外部改表造成类型漂移：DECIMAL → VARCHAR
    jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `amount` VARCHAR(64)");

    // 重新发布：validateForPublish 应在 align 之前拦截（409 META_DOMAIN_ERROR），而非发布成功后运行期 500
    mockMvc
        .perform(post("/api/v1/metadata/entities/" + entityId + "/publish"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.data.errorCode").value("META_DOMAIN_ERROR"));
  }

  @Test
  @DisplayName("端到端：列表按 moduleId 收敛（G2）——未归属指定模块的实体不出现")
  void list_filterByModuleId_excludesUnassigned() throws Exception {
    String suffix = String.valueOf(System.nanoTime());
    String codeA = "bp_mod_a_" + suffix;
    String codeB = "bp_mod_b_" + suffix;
    createEntity(codeA, "bp_mod_a_" + suffix, 0);
    createEntity(codeB, "bp_mod_b_" + suffix, 0);

    // 不带 moduleId：两个未归属实体都应出现在全量列表
    mockMvc
        .perform(get("/api/v1/metadata/entities").param("pageNum", "1").param("pageSize", "50"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.list[*].code").value(org.hamcrest.Matchers.hasItems(codeA, codeB)));

    // 带 moduleId=999999（不存在的模块）：两个未归属实体都不应被返回（服务端按 moduleId 收敛）
    String body =
        mockMvc
            .perform(
                get("/api/v1/metadata/entities")
                    .param("moduleId", "999999")
                    .param("pageNum", "1")
                    .param("pageSize", "50"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> codes =
        objectMapper.readTree(body).get("data").get("list").findValuesAsText("code");
    assertThat(codes).doesNotContain(codeA);
    assertThat(codes).doesNotContain(codeB);
  }

  @Test
  @DisplayName("端到端：建模工作台闭环——validate → copy（字段随迁）→ publish-preview → publish")
  void workbenchLoop_validateCopyPreviewPublish() throws Exception {
    String suffix = String.valueOf(System.nanoTime());
    String entityCode = "bp_ws_sim_" + suffix;
    String tableName = "bp_ws_sim_" + suffix;

    // 1) 建模 RUNTIME 实体 + 字段
    String entityId = createEntity(entityCode, tableName, 1);
    createField(entityId, "amount", "DECIMAL", true, false);
    createField(entityId, "remark", "STRING", false, false);

    // 2) 静态校验：可执行（未归属模块仅有 WARNING，不阻断）
    mockMvc
        .perform(get("/api/v1/metadata/entities/" + entityId + "/validate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray());

    // 3) 复制实体：定义 + 字段随迁为新草稿
    Map<String, Object> copyCmd =
        Map.of("code", entityCode + "_cp", "tableName", tableName + "_cp");
    String copyResp =
        mockMvc
            .perform(
                post("/api/v1/metadata/entities/" + entityId + "/copy")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(copyCmd)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String copyId = objectMapper.readTree(copyResp).get("data").asText();
    createdEntityIds.add(copyId);
    createdTableNames.add(tableName + "_cp");

    // 复制体字段数与源一致（2 个字段全部随迁）
    String copyFields =
        mockMvc
            .perform(
                get("/api/v1/metadata/entities/" + copyId + "/fields")
                    .param("pageNum", "1")
                    .param("pageSize", "50"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(copyFields).get("data").get("list").size()).isEqualTo(2);

    // 4) 发布摘要预览：RUNTIME 实体附物理计划（整表不存在 → createTable=true），可发布
    String preview =
        mockMvc
            .perform(get("/api/v1/metadata/entities/" + entityId + "/publish-preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var previewData = objectMapper.readTree(preview).get("data");
    System.out.println("[DEBUG-preview] " + preview);
    assertThat(previewData.get("runnable").asBoolean()).isTrue();
    assertThat(previewData.get("physical").get("createTable").asBoolean()).isTrue();
    assertThat(previewData.get("fields").size()).isEqualTo(2);

    // 5) 按预览结论发布 → 建物理表
    mockMvc
        .perform(post("/api/v1/metadata/entities/" + entityId + "/publish"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  // ===================== 辅助 =====================

  private String createEntity(String code, String tableName, int deliveryMode) throws Exception {
    var cmd = new CreateMetaEntityCommand();
    cmd.setName(code);
    cmd.setCode(code);
    cmd.setDisplayName(code);
    cmd.setTableName(tableName);
    cmd.setType(0);
    cmd.setDeliveryMode(deliveryMode);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/metadata/entities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cmd)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String entityId = objectMapper.readTree(resp).get("data").asText();
    createdEntityIds.add(entityId);
    createdTableNames.add(tableName);
    return entityId;
  }

  private void createField(
      String entityId, String code, String type, boolean required, boolean unique)
      throws Exception {
    Map<String, Object> body =
        Map.of(
            "name", code,
            "code", code,
            "displayName", code,
            "type", type,
            "required", required,
            "unique", unique);
    mockMvc
        .perform(
            post("/api/v1/metadata/entities/" + entityId + "/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));
  }

  // 自清理统一在 @AfterEach clearTenantAndCleanup 中执行
}
