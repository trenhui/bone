package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.security.jwt.JwtTokenService;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryEntityKeyMappingAdapter;
import com.bone.masterdata.testsupport.supplier.InMemoryLegalEntityRepositoryPort;
import com.bone.masterdata.testsupport.supplier.InMemoryPaymentBlockReleaseReview;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierExitFacts;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierOnboardingFacts;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierRepositoryPort;
import com.bone.masterdata.testsupport.supplier.InMemoryVendorGateFacts;
import com.bone.masterdata.testsupport.supplier.InMemoryVendorRepositoryPort;
import com.bone.masterdata.testsupport.supplier.SupplierHttpTestConfiguration;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 供应商治理端点的真实 HTTP 联调：完整 Spring Boot 启动 + JWT 鉴权 + 内存端口夹具。
 *
 * <p>覆盖五类边界：
 *
 * <ol>
 *   <li><b>响应包装与分页</b>：{@code ApiResponse<PageResult<T>>} 的实际 JSON 形状，而不是只看类型声明；
 *   <li><b>状态机与守卫</b>：两条迁移的全部前置条件（准入要变更请求批准 + 已存在 ACTIVE_VENDOR；退出要上游结清 + Vendor 已关闭）；
 *   <li><b>命令瘦身</b>：请求体里<b>没有</b>任何守卫事实字段，负向用例只能改夹具 —— 若某天有人把事实放回请求体，本类会立刻失去区分力并红；
 *   <li><b>租户与权限</b>：自报跨租户 fail-closed、缺写权限 403 且零副作用；
 *   <li><b>PII 边界</b>：任何响应体都不出现电话、统一社会信用代码与法人名称（治理端只回裁决与摘要）。
 * </ol>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "bone.procurement.supplier-mdm.enabled=true")
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, SupplierHttpTestConfiguration.class})
class SupplierHttpIntegrationTest {

  private static final String BASE = "/api/v1/masterdata/suppliers";
  private static final String PARTY = "SUP-1";
  private static final String PHONE = "13800009527";
  private static final String USCC = "91310000MA1FL0AB2C";
  private static final String OTHER_USCC = "91310000MA1FL0AB2D";
  private static final String LEGAL_NAME = "上海某某供应链科技有限公司";

  @Autowired private TestRestTemplate rest;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private InMemorySupplierRepositoryPort suppliers;
  @Autowired private InMemoryVendorRepositoryPort vendors;
  @Autowired private InMemoryEntityKeyMappingAdapter xref;
  @Autowired private InMemoryLegalEntityRepositoryPort legalEntities;
  @Autowired private InMemorySupplierExitFacts exitFacts;
  @Autowired private InMemorySupplierOnboardingFacts onboardingFacts;
  @Autowired private InMemoryVendorGateFacts gateFacts;
  @Autowired private InMemoryPaymentBlockReleaseReview releaseReview;

  private HttpHeaders writeHeaders;

  @BeforeEach
  void reset() {
    suppliers.reset();
    vendors.reset();
    xref.reset();
    // 法人身份端口同样是 Spring 单例：不重置会让后跑的用例继承前一个用例的身份唯一性判定。
    legalEntities.reset();
    exitFacts.reset();
    onboardingFacts.reset();
    gateFacts.reset();
    releaseReview.reset();
    writeHeaders = headers(List.of("masterdata:governance:write", "masterdata:governance:read"));
  }

  @Test
  @DisplayName("HTTP 全链路：登记 → 开通 Vendor → 两级推进 → 退出，四道前置逐一拦截后才放行")
  void registerThenAdvanceToExitedOverHttp() {
    JsonNode registered =
        post(BASE + "/registrations", registerBody(1L, PARTY, "L-1", USCC), writeHeaders);
    assertThat(registered.path("success").asBoolean()).isTrue();
    assertThat(registered.path("data").path("outcome").asText()).isEqualTo("APPLIED");
    assertThat(registered.path("data").path("supplierState").asText()).isEqualTo("PROSPECT");
    assertThat(registered.path("data").path("vendorState").asText()).isEqualTo("PENDING_VENDOR");
    assertThat(suppliers.countByTenant(1L)).isEqualTo(1L);
    assertThat(vendors.findCurrent(1L, PARTY)).isPresent();

    JsonNode submitted = postSupplier(PARTY, "onboarding_submitted");
    assertThat(submitted.path("data").path("toState").asText()).isEqualTo("ONBOARDING");

    // 准入第一道：变更请求未全段批准 → 拒绝（夹具默认 fail-closed，请求体无从影响）。
    JsonNode notApproved = postSupplier(PARTY, "onboarding_approved");
    assertThat(notApproved.path("success").asBoolean()).isFalse();
    assertThat(notApproved.path("message").asText()).contains("CHANGE_REQUEST_NOT_FULLY_APPROVED");

    // 准入第二道：即使变更请求已批，Vendor 仍是 PENDING_VENDOR → 拒绝。
    onboardingFacts.approve();
    JsonNode noActiveVendor = postSupplier(PARTY, "onboarding_approved");
    assertThat(noActiveVendor.path("success").asBoolean()).isFalse();
    assertThat(noActiveVendor.path("message").asText()).contains("NO_ACTIVE_VENDOR");

    gateFacts.passAllGates();
    JsonNode activated = postVendor(PARTY, "activation_passed", "DOMAIN");
    assertThat(activated.path("data").path("toState").asText()).isEqualTo("ACTIVE_VENDOR");

    JsonNode approved = postSupplier(PARTY, "onboarding_approved");
    assertThat(approved.path("data").path("toState").asText()).isEqualTo("ACTIVE");

    // 退出第一道：上游有未结 PO → 拒绝，且不落库（状态仍是 ACTIVE）。
    exitFacts.withOpenPurchaseOrders(2);
    JsonNode rejected = postSupplier(PARTY, "exit_approved");
    assertThat(rejected.path("success").asBoolean()).isFalse();
    assertThat(rejected.path("data").path("errorCode").asText()).isNotEmpty();
    assertThat(suppliers.findCurrent(1L, PARTY).orElseThrow().state().code()).isEqualTo("ACTIVE");

    // 退出第二道：上游已结清，但 Vendor 仍 ACTIVE_VENDOR（未关闭的 Vendor 角色）→ 拒绝。
    exitFacts.reset();
    JsonNode openVendor = postSupplier(PARTY, "exit_approved");
    assertThat(openVendor.path("success").asBoolean()).isFalse();
    assertThat(openVendor.path("message").asText()).contains("OPEN_VENDOR_ONBOARDING");

    assertThat(postVendor(PARTY, "deactivate", "DOMAIN").path("data").path("toState").asText())
        .isEqualTo("INACTIVE_VENDOR");
    JsonNode exited = postSupplier(PARTY, "exit_approved");
    assertThat(exited.path("data").path("toState").asText()).isEqualTo("EXITED");

    // 终态：表里没有 from 行，任何事件都拒绝。
    JsonNode terminal = postSupplier(PARTY, "restored");
    assertThat(terminal.path("success").asBoolean()).isFalse();

    JsonNode state = get(BASE + "/" + PARTY, writeHeaders);
    assertThat(state.path("data").path("supplierState").asText()).isEqualTo("EXITED");
    assertThat(state.path("data").path("supplierHistory").toString())
        .contains("ACTIVE->EXITED (exit_approved)");
    assertPiiFree(
        registered,
        submitted,
        notApproved,
        noActiveVendor,
        activated,
        approved,
        rejected,
        openVendor,
        exited,
        terminal,
        state);
  }

  @Test
  @DisplayName("Vendor 闸门：五项齐备才开通；付款冻结的提起与解除都只接受外部财务事件")
  void vendorGateAndPaymentBlockSourceAreEnforced() {
    post(BASE + "/registrations", registerBody(1L, PARTY, "L-1", USCC), writeHeaders);

    // 闸门缺一项 → 拒绝，状态不动。
    gateFacts.passAllGates();
    gateFacts.failBankVerification();
    JsonNode blocked = postVendor(PARTY, "activation_passed", "DOMAIN");
    assertThat(blocked.path("success").asBoolean()).isFalse();
    assertThat(blocked.path("message").asText()).contains("BANK_NOT_VERIFIED");
    assertThat(vendors.findCurrent(1L, PARTY).orElseThrow().state().code())
        .isEqualTo("PENDING_VENDOR");

    gateFacts.passAllGates();
    JsonNode activated = postVendor(PARTY, "activation_passed", "DOMAIN");
    assertThat(activated.path("data").path("toState").asText()).isEqualTo("ACTIVE_VENDOR");
    assertThat(activated.path("data").path("basis").asText()).isEqualTo("ACTIVATION_GATE_PASSED");

    // 域内来源不得自行提起冻结（设计 D7 覆盖两个方向）。
    JsonNode deniedRaise = postVendor(PARTY, "payment_block_raised", "DOMAIN");
    assertThat(deniedRaise.path("success").asBoolean()).isFalse();
    assertThat(deniedRaise.path("message").asText()).contains("外部财务事件");

    JsonNode raised = postVendor(PARTY, "payment_block_raised", "EXTERNAL_FINANCE");
    assertThat(raised.path("data").path("toState").asText()).isEqualTo("PAYMENT_BLOCKED");

    // 域内来源不得自行解除冻结。
    JsonNode deniedRelease = postVendor(PARTY, "payment_block_released", "DOMAIN");
    assertThat(deniedRelease.path("success").asBoolean()).isFalse();
    assertThat(vendors.findCurrent(1L, PARTY).orElseThrow().state().code())
        .isEqualTo("PAYMENT_BLOCKED");

    // 外部来源但无人工复核留痕 → 仍拒绝（spec:83 两个条件）。
    JsonNode untraced = postVendor(PARTY, "payment_block_released", "EXTERNAL_FINANCE");
    assertThat(untraced.path("success").asBoolean()).isFalse();
    assertThat(untraced.path("message").asText()).contains("人工复核留痕");

    releaseReview.trace();
    JsonNode released = postVendor(PARTY, "payment_block_released", "EXTERNAL_FINANCE");
    assertThat(released.path("data").path("toState").asText()).isEqualTo("ACTIVE_VENDOR");

    assertPiiFree(blocked, activated, deniedRaise, raised, deniedRelease, untraced, released);
  }

  @Test
  @DisplayName("自报跨租户 tenantId 被 fail-closed，且不产生任何落库")
  void crossTenantRequestShouldBeRejected() {
    JsonNode resp =
        post(BASE + "/registrations", registerBody(2L, PARTY, "L-1", USCC), writeHeaders);

    assertThat(resp.path("success").asBoolean()).isFalse();
    assertThat(suppliers.countByTenant(1L)).isZero();
    assertThat(suppliers.countByTenant(2L)).isZero();
    assertThat(vendors.saveCount()).isZero();
    assertThat(xref.registerCount()).isZero();
  }

  @Test
  @DisplayName("缺写权限：403 且零副作用（读权限 token 不能登记也不能推进状态）")
  void missingWriteAuthorityShouldBeRejected() {
    HttpHeaders readOnly = headers(List.of("masterdata:governance:read"));

    ResponseEntity<String> registerResp =
        exchange(BASE + "/registrations", registerBody(1L, PARTY, "L-1", USCC), readOnly);
    assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = parse(registerResp);
    assertThat(body.path("success").asBoolean()).isFalse();
    assertThat(body.path("data").path("errorCode").asText())
        .as("403 必须带机器可读错误码，前端不能靠解析中文文案判断")
        .isNotEmpty();
    assertThat(suppliers.saveCount()).isZero();
    assertThat(vendors.saveCount()).isZero();
    assertThat(xref.registerCount()).isZero();
  }

  @Test
  @DisplayName("分页：登录租户内按 partyId 稳定排序，返回 PageResult 形状")
  void pageReturnsWrappedPageResult() {
    post(BASE + "/registrations", registerBody(1L, PARTY, "L-1", USCC), writeHeaders);
    // 第二个主体必须换统一社会信用代码：同码会被身份唯一性判成转人工，分页就只剩 1 条（假绿会掩盖这条冲突）。
    post(BASE + "/registrations", registerBody(1L, "SUP-2", "L-2", OTHER_USCC), writeHeaders);

    JsonNode page = get(BASE + "?page=1&size=10", writeHeaders);
    assertThat(page.path("success").asBoolean()).isTrue();
    JsonNode data = page.path("data");
    assertThat(data.path("total").asLong()).isEqualTo(2L);
    assertThat(data.path("page").asInt()).isEqualTo(1);
    assertThat(data.path("size").asInt()).isEqualTo(10);
    // PageResult.getList() 带 @JsonIgnore，线上 JSON 的数组字段名是 records —— 断言按真实序列化契约写，
    // 不能按 Java getter 名推测（按 list 断言会在 MissingNode 上静默得到 0，假绿/假红都可能）。
    assertThat(data.path("records").size()).isEqualTo(2);
    assertThat(data.path("records").get(0).path("partyId").asText()).isEqualTo("SUP-1");
    assertThat(data.path("records").get(1).path("partyId").asText()).isEqualTo("SUP-2");
    assertPiiFree(page);

    // page=0 / size 越界被夹紧而不是 5xx。
    JsonNode clamped = get(BASE + "?page=0&size=9999", writeHeaders);
    assertThat(clamped.path("success").asBoolean()).isTrue();
    assertThat(clamped.path("data").path("page").asInt()).isEqualTo(1);
    assertThat(clamped.path("data").path("size").asInt()).isEqualTo(200);
  }

  @Test
  @DisplayName("未知主体返回 data=null 而不是 5xx")
  void unknownPartyReturnsNullPayload() {
    JsonNode resp = get(BASE + "/SUP-NOT-EXIST", writeHeaders);
    assertThat(resp.path("success").asBoolean()).isTrue();
    assertThat(resp.path("data").isNull()).isTrue();
  }

  // ---------------------------------------------------------------- helpers

  /** PII 边界（spec Scenario「治理端不含字段原值」）：电话、统一社会信用代码、法人名称一律不得出现在响应体里。 */
  private void assertPiiFree(JsonNode... bodies) {
    for (JsonNode body : bodies) {
      String raw = body.toString();
      // 两个主体的 USCC 都要断言：只查第一个会让「第二个主体的身份主键泄漏」静默通过。
      assertThat(raw)
          .doesNotContain(PHONE)
          .doesNotContain(USCC)
          .doesNotContain(OTHER_USCC)
          .doesNotContain(LEGAL_NAME);
    }
  }

  private JsonNode postSupplier(String partyId, String event) {
    return post(
        BASE + "/" + partyId + "/supplier-transitions", transitionBody(event), writeHeaders);
  }

  private JsonNode postVendor(String partyId, String event, String source) {
    return post(
        BASE + "/" + partyId + "/vendor-transitions", vendorBody(event, source), writeHeaders);
  }

  private HttpHeaders headers(List<String> scopes) {
    String token = jwtTokenService.generateToken(1L, "procurement-e2e-tester", 1L, scopes);
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(token);
    h.add("X-Tenant-Id", "1");
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  private ResponseEntity<String> exchange(String path, Object body, HttpHeaders headers) {
    try {
      return rest.exchange(
          path,
          HttpMethod.POST,
          new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
          String.class);
    } catch (Exception e) {
      throw new AssertionError("POST " + path + " 失败", e);
    }
  }

  private JsonNode post(String path, Object body, HttpHeaders headers) {
    return parse(exchange(path, body, headers));
  }

  private JsonNode get(String path, HttpHeaders headers) {
    try {
      ResponseEntity<String> resp =
          rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
      return parse(resp);
    } catch (Exception e) {
      throw new AssertionError("GET " + path + " 失败", e);
    }
  }

  private JsonNode parse(ResponseEntity<String> resp) {
    try {
      return objectMapper.readTree(resp.getBody());
    } catch (Exception e) {
      throw new AssertionError("响应体不是 JSON: " + resp.getBody(), e);
    }
  }

  private static Map<String, Object> registerBody(
      long tenantId, String partyId, String localId, String uscc) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("mergeId", "SMR-" + localId);
    body.put("tenantId", tenantId);
    body.put("partyId", partyId);
    body.put("sourceSystem", "ERP");
    body.put("localId", localId);
    body.put("mergedAt", "2026-11-14T02:00:00Z");
    Map<String, Object> erp = new LinkedHashMap<>();
    erp.put("legal_name", LEGAL_NAME);
    erp.put("unified_social_credit_code", uscc);
    erp.put("contact_phone", PHONE);
    Map<String, Object> srm = new LinkedHashMap<>();
    srm.put("city", "上海");
    body.put("sources", List.of(source("R1", "ERP", 1, erp), source("R2", "SRM", 2, srm)));
    return body;
  }

  /** 命令只剩「对哪个主体做什么事件」：守卫事实一律由端口提供，请求体里没有任何可自报的字段。 */
  private static Map<String, Object> transitionBody(String event) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tenantId", 1);
    body.put("event", event);
    return body;
  }

  private static Map<String, Object> vendorBody(String event, String source) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tenantId", 1);
    body.put("event", event);
    body.put("source", source);
    return body;
  }

  private static Map<String, Object> source(
      String recordId, String system, int rank, Map<String, Object> values) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("recordId", recordId);
    s.put("sourceSystem", system);
    s.put("trustRank", rank);
    s.put("observedAt", "2026-11-14T01:59:50Z");
    s.put("values", values);
    return s;
  }
}
