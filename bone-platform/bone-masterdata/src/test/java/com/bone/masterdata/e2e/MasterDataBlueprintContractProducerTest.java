package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.security.jwt.JwtTokenService;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A 段（生产者侧契约验证）：bone-masterdata ↔ bone-blueprint 全链路的一端。
 *
 * <p><b>目的</b>：在<b>无 MySQL / Redis / IAM / 网关</b>的本机环境里，用 H2 把 masterdata 真实起起来， 模拟人工在治理后台「建模实体 →
 * 录入记录 → 发布」，并断言其 REST 输出<b>正是</b> {@code MasterDataAclAdapter}（bone-blueprint）所消费的 JSON 形态：
 *
 * <ul>
 *   <li>实体列表 / 详情：含 {@code entityCode} 且 {@code status == "PUBLISHED"}；
 *   <li>记录列表：{@code data} 为 JSON
 *       字符串且含业务键字段（code/name/price、customer_code/level_code、level_code/discount_rate），{@code status
 *       == "PUBLISHED"}。
 * </ul>
 *
 * <p><b>为什么用真实 HTTP（RANDOM_PORT）</b>：适配器跨服务消费的就是 HTTP JSON，只有真实走一次 HTTP 才能证明
 * 输出形态与契约一致（而非仅内存对象相等）。本段只验证「生产者形态」，B 段验证「消费者解析」。
 *
 * <p><b>租户</b>：masterdata 的 {@code WebTenantConfiguration} 从 {@code X-Tenant-Id} 头注入租户（缺头则不回落）；
 * 本测试用合法 JWT 通过认证，并显式带 {@code X-Tenant-Id: 1}，与网关注入语义一致。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
class MasterDataBlueprintContractProducerTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate rest;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  private HttpHeaders authHeaders;

  @BeforeEach
  void prepareAuth() {
    // 走真实 HTTP 需经 Filter 链鉴权，而写端点均有方法级 @PreAuthorize，故 token 必须带足 scopes：
    // 建实体/发实体 → entities:write；造记录 → records:write；发布记录 → records:approve。
    // 权限码与各 Controller 的 @PreAuthorize 一一对应，缺一个即被全局异常处理器转成 success=false。
    String token =
        jwtTokenService.generateToken(
            1L,
            "e2e-tester",
            1L,
            List.of(
                "masterdata:entities:write",
                "masterdata:records:write",
                "masterdata:records:approve"));
    authHeaders = new HttpHeaders();
    authHeaders.setBearerAuth(token);
    authHeaders.add("X-Tenant-Id", "1");
    authHeaders.setContentType(MediaType.APPLICATION_JSON);
  }

  @AfterEach
  void cleanTables() {
    jdbcTemplate.update("DELETE FROM mdm_record_version");
    jdbcTemplate.update("DELETE FROM mdm_record");
    jdbcTemplate.update("DELETE FROM mdm_entity");
  }

  @Test
  @DisplayName("A段-全链路生产者：建实体+记录并发布，REST 输出形态与 blueprint 适配器契约一致")
  void producerEmitsContractShapedPayload() throws Exception {
    // ===== 1. 建模三个主数据实体（编码是下游消费业务键） =====
    long productEntityId = createEntity("商品主数据", "MD_PRODUCT");
    long customerEntityId = createEntity("客户主数据", "CUSTOMER");
    long levelEntityId = createEntity("客户等级折扣率", "CUSTOMER_LEVEL");

    publishEntity(productEntityId);
    publishEntity(customerEntityId);
    publishEntity(levelEntityId);

    // ===== 2. 录入并发布记录（模拟人工数据录入） =====
    long productRecId =
        createRecord(productEntityId, Map.of("code", "P001", "name", "测试商品", "price", 99.0));
    long customerRecId =
        createRecord(customerEntityId, Map.of("customer_code", "C001", "level_code", "VIP"));
    long levelRecId =
        createRecord(levelEntityId, Map.of("level_code", "VIP", "discount_rate", 0.88));

    publishRecord(productRecId);
    publishRecord(customerRecId);
    publishRecord(levelRecId);

    // ===== 3. 断言实体输出形态（适配器按 entityCode + status=PUBLISHED 定位） =====
    JsonNode productEntity = getEntityDetail(productEntityId);
    assertThat(productEntity.path("entityCode").asText()).isEqualTo("MD_PRODUCT");
    assertThat(productEntity.path("status").asText()).isEqualTo("PUBLISHED");

    // ===== 4. 断言记录输出形态（适配器按 masterDataEntityId + status=PUBLISHED 拉取，data 为 JSON 字符串） =====
    JsonNode productRecords = listRecords(productEntityId);
    JsonNode productRec0 = productRecords.path(0);
    assertThat(productRec0.path("status").asText()).isEqualTo("PUBLISHED");
    // data 必须是「字符串形态的 JSON」（适配器对 textual 走 readValue 分支）
    assertThat(productRec0.path("data").isTextual()).isTrue();
    JsonNode productData = objectMapper.readTree(productRec0.path("data").asText());
    assertThat(productData.path("code").asText()).isEqualTo("P001");
    assertThat(productData.path("name").asText()).isEqualTo("测试商品");
    assertThat(productData.path("price").asDouble()).isEqualTo(99.0);

    JsonNode customerRec0 = listRecords(customerEntityId).path(0);
    JsonNode customerData = objectMapper.readTree(customerRec0.path("data").asText());
    assertThat(customerData.path("customer_code").asText()).isEqualTo("C001");
    assertThat(customerData.path("level_code").asText()).isEqualTo("VIP");

    JsonNode levelRec0 = listRecords(levelEntityId).path(0);
    JsonNode levelData = objectMapper.readTree(levelRec0.path("data").asText());
    assertThat(levelData.path("level_code").asText()).isEqualTo("VIP");
    assertThat(levelData.path("discount_rate").asDouble()).isEqualTo(0.88);
  }

  // ===================== 造数辅助 =====================

  private long createEntity(String name, String entityCode) throws Exception {
    Map<String, Object> body =
        Map.of("name", name, "entityCode", entityCode, "description", name, "category", "default");
    JsonNode resp =
        exchange(
            HttpMethod.POST, "/api/v1/masterdata/entities", objectMapper.writeValueAsString(body));
    assertThat(resp.path("success").asBoolean()).isTrue();
    return resp.path("data").asLong();
  }

  private void publishEntity(long id) {
    JsonNode resp = exchange(HttpMethod.POST, "/api/v1/masterdata/entities/" + id + "/publish", "");
    assertThat(resp.path("success").asBoolean()).isTrue();
  }

  private long createRecord(long entityId, Map<String, Object> data) throws Exception {
    Map<String, Object> body = Map.of("data", data);
    JsonNode resp =
        exchange(
            HttpMethod.POST,
            "/api/v1/masterdata/records/entity/" + entityId,
            objectMapper.writeValueAsString(body));
    assertThat(resp.path("success").asBoolean()).isTrue();
    return resp.path("data").asLong();
  }

  private void publishRecord(long id) {
    JsonNode resp = exchange(HttpMethod.POST, "/api/v1/masterdata/records/" + id + "/publish", "");
    assertThat(resp.path("success").asBoolean()).isTrue();
  }

  private JsonNode getEntityDetail(long id) {
    return exchange(HttpMethod.GET, "/api/v1/masterdata/entities/" + id, null).path("data");
  }

  private JsonNode listRecords(long entityId) {
    JsonNode resp =
        exchange(
            HttpMethod.GET,
            "/api/v1/masterdata/records?masterDataEntityId="
                + entityId
                + "&status=PUBLISHED&pageNum=1&pageSize=500",
            null);
    return resp.path("data").path("records");
  }

  private JsonNode exchange(HttpMethod method, String uri, String body) {
    HttpEntity<String> entity = new HttpEntity<>(body, authHeaders);
    String url = "http://localhost:" + port + uri;
    ResponseEntity<String> response = rest.exchange(url, method, entity, String.class);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("HTTP %s %s 应成功", method, uri)
        .isTrue();
    try {
      return objectMapper.readTree(response.getBody());
    } catch (Exception e) {
      throw new IllegalStateException("解析响应失败: " + response.getBody(), e);
    }
  }
}
