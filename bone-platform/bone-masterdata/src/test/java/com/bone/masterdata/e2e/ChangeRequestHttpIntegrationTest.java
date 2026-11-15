package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.security.jwt.JwtTokenService;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.bone.masterdata.testsupport.changerequest.ChangeRequestHttpTestConfiguration;
import com.bone.masterdata.testsupport.changerequest.InMemoryChangeRequestAdapter;
import com.bone.masterdata.testsupport.changerequest.InMemoryMasterDataProjection;
import com.bone.masterdata.testsupport.changerequest.InMemoryOutboxRelayStore;
import com.bone.masterdata.testsupport.changerequest.RecordingChangeRequestActivationAdapter;
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
 * 分段 CR 真实 HTTP 联调：Spring Boot RANDOM_PORT + JWT 多账号 + 终局端口夹具。
 *
 * <p>证明的是 API 契约与装配（条件开关、@PreAuthorize、操作人绑定、全局异常、脱敏序列化）， 而不是领域裁决本身；与纯 Java 编排联调互补。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "bone.masterdata.change-request.enabled=true",
      "bone.masterdata.outbox-relay.enabled=true",
      "bone.masterdata.outbox-relay.consumer-id=procura-srm",
      "bone.masterdata.outbox-relay.max-attempts=1"
    })
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, ChangeRequestHttpTestConfiguration.class})
class ChangeRequestHttpIntegrationTest {

  private static final String BASE = "/api/v1/masterdata/change-requests";
  private static final List<String> WRITE =
      List.of("masterdata:governance:write", "masterdata:governance:read");
  private static final List<String> READ = List.of("masterdata:governance:read");

  @Autowired private TestRestTemplate rest;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private InMemoryChangeRequestAdapter crPort;
  @Autowired private InMemoryOutboxRelayStore outbox;
  @Autowired private InMemoryMasterDataProjection projection;
  @Autowired private RecordingChangeRequestActivationAdapter activation;

  @BeforeEach
  void prepare() {
    crPort.reset();
    outbox.reset();
    projection.reset();
    activation.reset();
    projection.authorize(1L, "P-1", "procura-srm");
  }

  @Test
  @DisplayName("HTTP 全链路：创建 → 提交 → 三段并行审批(三个不同 JWT) → 应用 → 查询脱敏态")
  void fullChainOverHttp() {
    JsonNode created = post(BASE, createBody(1L, "101"), headers(WRITE, 101L));
    assertThat(created.path("success").asBoolean()).isTrue();
    assertThat(created.path("data").path("status").asText()).isEqualTo("DRAFT");

    JsonNode submitted =
        post(BASE + "/CR-1/submit", Map.of("tenantId", 1, "operator", "101"), headers(WRITE, 101L));
    assertThat(submitted.path("data").path("status").asText()).isEqualTo("IN_REVIEW");

    decide("GENERAL", "201", 201L);
    decide("COMPANY_CODE", "202", 202L);
    decide("PURCHASING_ORG", "203", 203L);

    JsonNode applied =
        post(BASE + "/CR-1/apply", Map.of("tenantId", 1, "applier", "301"), headers(WRITE, 301L));
    assertThat(applied.path("success").asBoolean()).isTrue();
    assertThat(applied.path("data").path("status").asText()).isEqualTo("APPLIED");
    assertThat(applied.path("data").path("distinctApprovers").asInt()).isEqualTo(3);
    assertThat(applied.path("data").path("activated").asBoolean()).isTrue();
    assertThat(applied.toString()).doesNotContain("91310000MA1FL0AB2C");

    JsonNode state = get(BASE + "/CR-1", headers(READ, 101L));
    assertThat(state.path("data").path("segments").size()).isEqualTo(3);
    assertThat(activation.activationCount()).isEqualTo(1);
    assertThat(outbox.size()).isEqualTo(1);
  }

  @Test
  @DisplayName("缺写权限的 token 不能创建 CR，且零落库")
  void missingWriteAuthorityRejected() {
    JsonNode resp = post(BASE, createBody(1L, "101"), headers(READ, 101L));
    assertThat(resp.path("success").asBoolean()).isFalse();
    assertThat(crPort.saveCount()).isZero();
  }

  @Test
  @DisplayName("请求体代签他人姓名被 fail-closed")
  void actorSpoofingRejected() {
    JsonNode created = post(BASE, createBody(1L, "101"), headers(WRITE, 101L));
    assertThat(created.path("success").asBoolean()).isTrue();
    JsonNode resp =
        post(BASE + "/CR-1/submit", Map.of("tenantId", 1, "operator", "999"), headers(WRITE, 101L));
    assertThat(resp.path("success").asBoolean()).isFalse();
    assertThat(crPort.saveCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("自报跨租户 tenantId 被 fail-closed，且零落库")
  void crossTenantRejected() {
    JsonNode resp = post(BASE, createBody(2L, "101"), headers(WRITE, 101L));
    assertThat(resp.path("success").asBoolean()).isFalse();
    assertThat(crPort.saveCount()).isZero();
  }

  // ---------------------------------------------------------------- helpers

  private void decide(String segment, String approver, long userId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tenantId", 1);
    body.put("approved", true);
    body.put("approver", approver);
    JsonNode resp =
        post(BASE + "/CR-1/segments/" + segment + "/decide", body, headers(WRITE, userId));
    assertThat(resp.path("success").asBoolean()).isTrue();
  }

  private HttpHeaders headers(List<String> scopes, long userId) {
    String token = jwtTokenService.generateToken(userId, "mdm-e2e-" + userId, 1L, scopes);
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

  private static Map<String, Object> createBody(long tenantId, String createdBy) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("changeRequestId", "CR-1");
    body.put("tenantId", tenantId);
    body.put("partyId", "P-1");
    body.put("targetVersion", 7);
    body.put("changeDescription", "新增供应商开户行");
    body.put("createdBy", createdBy);
    body.put("createdAt", "2026-11-13T02:00:00Z");
    Map<String, Object> owners = new LinkedHashMap<>();
    owners.put("GENERAL", "201");
    owners.put("COMPANY_CODE", "202");
    owners.put("PURCHASING_ORG", "203");
    body.put("segmentOwners", owners);
    return body;
  }
}
