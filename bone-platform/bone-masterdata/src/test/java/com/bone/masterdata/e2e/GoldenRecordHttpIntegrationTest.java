package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.security.jwt.JwtTokenService;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.bone.masterdata.testsupport.goldenrecord.GoldenRecordHttpTestConfiguration;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryEntityKeyMappingAdapter;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryGoldenRecordAdapter;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryMasterDataOutboxAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Golden Record 真实 HTTP 联调：Spring Boot 完整启动 + JWT 鉴权 + 内存端口夹具，走一遍 POST /merge → GET /{partyId} →
 * POST /rollback，并验证权限/租户/PII 边界。
 *
 * <p>与纯 Java 编排联调互补：这里证明的是<b>API 层契约与 Spring 装配</b>（条件开关、@PreAuthorize、 全局异常处理、脱敏序列化），而不是领域裁决本身。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "bone.masterdata.golden-record.enabled=true",
      "bone.masterdata.golden-record.digest-pepper=http-integration-pepper-2026-11-13"
    })
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, GoldenRecordHttpTestConfiguration.class})
class GoldenRecordHttpIntegrationTest {

  private static final String BASE = "/api/v1/masterdata/golden-records";
  private static final String PHONE = "13800000001";

  @Autowired private TestRestTemplate rest;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private InMemoryGoldenRecordAdapter golden;
  @Autowired private InMemoryEntityKeyMappingAdapter xref;
  @Autowired private InMemoryMasterDataOutboxAdapter outbox;

  private HttpHeaders writeHeaders;

  @BeforeEach
  void prepare() {
    golden.reset();
    xref.reset();
    outbox.reset();
    writeHeaders = headers(List.of("masterdata:governance:write", "masterdata:governance:read"));
  }

  @Test
  @DisplayName("HTTP 全链路：合并 → 查询脱敏状态 → 整笔回滚，响应不含字段值")
  void mergeGetRollbackOverHttp() throws Exception {
    JsonNode merged = post(BASE + "/merge", mergeBody(1L), writeHeaders);
    assertThat(merged.path("success").asBoolean()).isTrue();
    assertThat(merged.path("data").path("outcome").asText()).isEqualTo("APPLIED");
    assertThat(merged.path("data").path("version").asLong()).isEqualTo(1L);
    assertThat(merged.toString()).doesNotContain(PHONE);

    JsonNode state = get(BASE + "/P-1", writeHeaders);
    assertThat(state.path("success").asBoolean()).isTrue();
    assertThat(state.path("data").path("fieldCodes").toString()).contains("legal_name");
    assertThat(state.toString()).doesNotContain(PHONE);

    JsonNode rolledBack =
        post(
            BASE + "/rollback",
            Map.of(
                "tenantId", 1,
                "mergeId", "M1",
                "reason", "主体识别错误",
                "operator", "steward-01"),
            writeHeaders);
    assertThat(rolledBack.path("success").asBoolean()).isTrue();
    assertThat(rolledBack.path("data").path("outcome").asText())
        .isEqualTo("GOLDEN_RECORD_ROLLED_BACK");
    assertThat(rolledBack.path("data").path("version").asLong()).isEqualTo(2L);
    assertThat(golden.findCurrent(1L, "P-1")).isEmpty();
    assertThat(outbox.pending()).hasSize(2);
  }

  @Test
  @DisplayName("自报跨租户 tenantId 被 fail-closed，且不产生任何落库")
  void crossTenantRequestShouldBeRejected() {
    JsonNode resp = post(BASE + "/merge", mergeBody(2L), writeHeaders);

    assertThat(resp.path("success").asBoolean()).isFalse();
    assertThat(golden.appendCount()).isZero();
    assertThat(xref.registerCount()).isZero();
    assertThat(outbox.pending()).isEmpty();
  }

  @Test
  @DisplayName("缺写权限的 token 不能触发合并")
  void missingWriteAuthorityShouldBeRejected() {
    HttpHeaders readOnly = headers(List.of("masterdata:governance:read"));
    JsonNode resp = post(BASE + "/merge", mergeBody(1L), readOnly);

    assertThat(resp.path("success").asBoolean()).isFalse();
    assertThat(golden.appendCount()).isZero();
  }

  @Test
  @DisplayName("身份字段冲突经 HTTP 转人工，且零副作用")
  void identityConflictOverHttpShouldHaveZeroSideEffects() {
    Map<String, Object> body = mergeBody(1L);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sources = (List<Map<String, Object>>) body.get("sources");
    @SuppressWarnings("unchecked")
    Map<String, Object> v1 = (Map<String, Object>) sources.get(0).get("values");
    @SuppressWarnings("unchecked")
    Map<String, Object> v2 = (Map<String, Object>) sources.get(1).get("values");
    v1.put("tax_id", "TAX-A");
    v2.put("tax_id", "TAX-B");

    JsonNode resp = post(BASE + "/merge", body, writeHeaders);
    assertThat(resp.path("success").asBoolean()).isTrue();
    assertThat(resp.path("data").path("outcome").asText()).isEqualTo("MANUAL_REVIEW_REQUIRED");
    assertThat(golden.appendCount()).isZero();
    assertThat(outbox.pending()).isEmpty();
  }

  // ---------------------------------------------------------------- helpers

  private HttpHeaders headers(List<String> scopes) {
    String token = jwtTokenService.generateToken(1L, "mdm-e2e-tester", 1L, scopes);
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(token);
    h.add("X-Tenant-Id", "1");
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  private JsonNode post(String path, Object body, HttpHeaders headers) {
    try {
      ResponseEntity<String> resp =
          rest.exchange(
              path,
              HttpMethod.POST,
              new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
              String.class);
      return objectMapper.readTree(resp.getBody());
    } catch (Exception e) {
      throw new AssertionError("POST " + path + " 失败", e);
    }
  }

  private JsonNode get(String path, HttpHeaders headers) {
    try {
      ResponseEntity<String> resp =
          rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
      return objectMapper.readTree(resp.getBody());
    } catch (Exception e) {
      throw new AssertionError("GET " + path + " 失败", e);
    }
  }

  private static Map<String, Object> mergeBody(long tenantId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("mergeId", "M1");
    body.put("tenantId", tenantId);
    body.put("partyId", "P-1");
    body.put("sourceSystem", "ERP");
    body.put("localId", "L-1");
    body.put("mergedAt", "2026-11-13T02:00:00Z");
    Map<String, Object> v1 = new LinkedHashMap<>();
    v1.put("legal_name", "上海某某科技有限公司");
    v1.put("unified_social_credit_code", "91310000MA1FL0AB2C");
    v1.put("contact_phone", PHONE);
    Map<String, Object> v2 = new LinkedHashMap<>();
    v2.put("city", "上海");
    body.put("sources", List.of(source("S1", "ERP", 1, v1), source("S2", "SRM", 2, v2)));
    return body;
  }

  private static Map<String, Object> source(
      String recordId, String system, int rank, Map<String, Object> values) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("recordId", recordId);
    s.put("sourceSystem", system);
    s.put("trustRank", rank);
    s.put("observedAt", "2026-11-13T01:59:50Z");
    s.put("values", values);
    return s;
  }
}
