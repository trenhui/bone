package com.bone.claim.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.security.jwt.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * 智能理赔端到端联调：完整启动 Spring 上下文（连本地 MySQL {@code bone} 库）， 经真实 Controller → Application → Domain →
 * Repository → DB 跑通主链路。
 *
 * <p>覆盖：建案 → 登记发票材料 → 主动解析（数电票文本层）→ 验真/验重 → 锁定 → 决策图执行 → 轨迹回放。 鉴权用 dev 共享密钥自签 JWT（与 {@code
 * JwtAuthenticationFilter} 同密钥验签）。 种子数据：租户 0 / 保单 {@code E2E-POLICY-0001} / 计划 9001 / 被保人 {@code
 * e2e-insured-hash-0001}。
 *
 * <p>本测试会向本地 {@code bone} 库写入真实理赔数据，<b>仅在本地联调环境运行</b>：需显式开启 （{@code -De2e.local=true} 或环境变量 {@code
 * E2E_LOCAL=on/1}）且本地 MySQL （{@code 127.0.0.1:3306}）可达，否则由 {@link LocalE2EEnvironment}
 * 在上下文加载前干净跳过，CI 不依赖本地库。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(LocalE2EEnvironment.class)
@TestPropertySource(
    properties = {
      "bone.datasource.url=jdbc:mysql://127.0.0.1:3306/bone",
      "bone.iam.jwt.secret-key=dev-only-secret-key-minimum-32-bytes-long"
    })
@DisplayName("智能理赔主链路端到端联调")
class ClaimLifecycleE2ETest {

  private static final String TENANT = "0";
  private static final long USER_ID = 1000L;
  private static final List<String> PERMISSIONS =
      List.of(
          "claim:claims:read", "claim:claims:write", "claim:review:write", "claim:decision:write");

  @Autowired private TestRestTemplate rest;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private ObjectMapper objectMapper;

  private HttpHeaders authHeaders() {
    return headersFor(Long.parseLong(TENANT));
  }

  /**
   * 指定租户的请求头：JWT 内签名 claim 与 {@code X-Tenant-Id} 同时切换。
   *
   * <p>两者必须一致——框架基类会用 token 内 claim 归一化 {@code X-Tenant-Id}，只改头不改 token 测的是"归一化有没有生效"，
   * 不是"租户边界有没有生效"，两类失败会长得一样。
   */
  private HttpHeaders headersFor(long tenantId) {
    String token = jwtTokenService.generateToken(USER_ID, "e2e-operator", tenantId, PERMISSIONS);
    HttpHeaders h = new HttpHeaders();
    h.setBearerAuth(token);
    h.set("X-Tenant-Id", String.valueOf(tenantId));
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  private JsonNode body(ResponseEntity<String> resp) {
    assertThat(resp.getStatusCode().is2xxSuccessful())
        .as("HTTP 2xx，实际 %s，body=%s", resp.getStatusCode(), resp.getBody())
        .isTrue();
    try {
      return objectMapper.readTree(resp.getBody()).get("data");
    } catch (Exception e) {
      throw new AssertionError("无法解析响应体：" + resp.getBody(), e);
    }
  }

  private <T> ResponseEntity<String> exchange(HttpMethod method, String url, Object reqBody) {
    return rest.exchange(url, method, new HttpEntity<>(reqBody, authHeaders()), String.class);
  }

  private <T> ResponseEntity<String> exchange(
      HttpMethod method, String url, Object reqBody, HttpHeaders headers) {
    return rest.exchange(url, method, new HttpEntity<>(reqBody, headers), String.class);
  }

  /**
   * 建案/追加必须带幂等键（缺失即 400 —— P0-3 fail-closed，服务端不代生成）。
   *
   * <p>本类原先不带该头，两个用例在 {@code -De2e.local=true} 下恒 400 —— 因为该 E2E 不进 CI（需本地库），
   * 这条破坏性变更没有在任何地方转红，门禁实质上停用了。此处按「一次请求一个键」补上；要验证重放语义的用例显式传入同一个键。
   */
  private HttpHeaders idempotentHeaders(String key) {
    HttpHeaders headers = authHeaders();
    headers.set(IdempotencyService.IDEMPOTENCY_KEY_HEADER, key);
    return headers;
  }

  private HttpHeaders idempotentHeaders() {
    return idempotentHeaders(UUID.randomUUID().toString());
  }

  @BeforeEach
  void warmup() {
    // 触发一次鉴权上下文，确保 JWT + 租户装配在正式流程前可用。
    authHeaders();
  }

  @Test
  @DisplayName("建案→材料→解析→验真→锁定→决策→轨迹 全链路贯通")
  void fullLifecycleRunsEndToEnd() throws Exception {
    // 1) 建案
    String contentHash = UUID.randomUUID().toString();
    String submit =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "policyNo", "E2E-POLICY-0001",
                "planId", 9001,
                "insuredIdHash", "e2e-insured-hash-0001",
                "outInsureTime", "2026-11-01T00:00:00Z",
                "claimType", "OUTPATIENT",
                "items", List.of(java.util.Map.of("itemName", "诊查费", "amount", 100.00))));
    JsonNode claim = body(exchange(HttpMethod.POST, "/api/v1/claims", submit, idempotentHeaders()));
    long claimId = claim.get("claimId").asLong();
    assertThat(claimId).isPositive();
    System.out.println("[E2E] 建案 claimId=" + claimId + " claimNo=" + claim.get("claimNo"));

    // 2) 登记发票材料（数电票）
    String register =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "fileRef",
                "e2e-file-" + contentHash,
                "contentHash",
                contentHash,
                "docType",
                "INVOICE",
                "verifyValid",
                "UNVERIFIABLE"));
    JsonNode evidence =
        body(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/evidences", register));
    long evidenceId = evidence.asLong();
    assertThat(evidenceId).isPositive();
    System.out.println("[E2E] 登记材料 evidenceId=" + evidenceId);

    // 3) 主动解析（数电票文本层）
    String rawText =
        "电子发票（普通发票）\n"
            + "发票代码：0440000123\n"
            + "发票号码：12345678\n"
            + "开票日期：2026-11-01\n"
            + "价税合计（小写）：¥1,234.56\n";
    JsonNode parse =
        body(
            exchange(
                HttpMethod.POST,
                "/api/v1/claims/" + claimId + "/evidences/" + evidenceId + "/parse",
                objectMapper.writeValueAsString(java.util.Map.of("rawText", rawText))));
    assertThat(parse).isNotNull();
    System.out.println("[E2E] 解析完成 extractedJson 含字段=" + parse.get("available"));

    // 4) 验真 / 验重（一期无税局通道，本地核验通过 = UNVERIFIABLE）
    JsonNode verify =
        body(
            exchange(
                HttpMethod.POST,
                "/api/v1/claims/" + claimId + "/evidences/" + evidenceId + "/verify",
                null));
    System.out.println("[E2E] 验真结果 verifyValid=" + verify.get("verifyValid"));

    // 5) 锁定证据集（理算前置）
    JsonNode lock =
        body(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/evidences/lock", null));
    assertThat(lock.asInt()).isGreaterThanOrEqualTo(1);
    System.out.println("[E2E] 锁定材料数=" + lock.asInt());

    // 5.5) 分配 → 进入初审（决策图执行前置：ALLOCATED 之后才能开始初审）
    JsonNode allocate = allocate(claimId);
    assertThat(allocate).isNotNull();
    JsonNode preExam = startPreExam(claimId);
    assertThat(preExam).isNotNull();
    System.out.println("[E2E] 分配 + 进入初审 OK");

    // 6) 执行决策图（自动放款开关默认关闭，结论应为人工/复核类，但不应报错）
    JsonNode decision = executeDecision(claimId);
    String traceId = decision.get("traceId").asText();
    assertThat(traceId).isNotBlank();
    System.out.println(
        "[E2E] 决策结论="
            + decision.get("decisionResult")
            + " tier="
            + decision.get("decisionTier")
            + " consumption="
            + decision.get("consumptionReason")
            + " traceId="
            + traceId);

    // 6.5) 结论必须被消费：案件离开「初审中」并落到该去的地方
    // 修复前实测：决策结论 MANUAL_REQUIRED 执行成功，案件仍停在 AUTO_PRE_EXAMINING（进不了核赔队列、
    // 不会结案、评测导出拿不到弱标签）。这里同时校验「响应里的落点」与「详情接口读到的落点」一致，
    // 避免只改了响应而没真正落库。
    String routedStatus = decision.get("claimStatus").asText();
    assertThat(routedStatus).isNotEqualTo("AUTO_PRE_EXAMINING");
    JsonNode detail = body(exchange(HttpMethod.GET, "/api/v1/claims/" + claimId, null));
    assertThat(detail.get(0).get("status").asText()).isEqualTo(routedStatus);
    System.out.println("[E2E] 案件落点=" + routedStatus + " routing=" + decision.get("routingReason"));

    // 7) 轨迹回放
    String traceUrl = "/api/v1/claims/" + claimId + "/decision/trace?traceId=" + traceId;
    JsonNode trace = body(exchange(HttpMethod.GET, traceUrl, null));
    assertThat(trace.size()).isGreaterThan(0);
    System.out.println("[E2E] 轨迹节点数=" + trace.size());
  }

  /**
   * 端点级跨租户反例——04 分册原写「留待接入测试基建后补」的那一条。
   *
   * <p><b>判据是「不可区分」而不是「被拒绝」</b>：跨租户读必须与"案件不存在"返回同一个 404；用 403 会泄露该 ID
   * 在别的租户真实存在，攻击者可据此枚举他人案件。同租户正例必须先跑通——否则 404 可能来自端点坏了或数据没落，反例就成了假绿。
   */
  @Test
  @DisplayName("跨租户读详情必须 404 且不泄露存在性（同租户正例作对照）")
  void crossTenantDetailIsIndistinguishableFromNotFound() throws Exception {
    String submit =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "policyNo",
                "E2E-POLICY-0001",
                "planId",
                9001,
                "insuredIdHash",
                "e2e-insured-hash-0001",
                "outInsureTime",
                "2026-11-01T00:00:00Z",
                "claimType",
                "OUTPATIENT",
                "items",
                List.of(java.util.Map.of("itemName", "诊查费", "amount", 100.00))));
    JsonNode claim =
        body(
            rest.exchange(
                "/api/v1/claims",
                HttpMethod.POST,
                new HttpEntity<>(submit, idempotentHeaders()),
                String.class));
    long claimId = claim.get("claimId").asLong();
    assertThat(claimId).isPositive();

    ResponseEntity<String> sameTenant =
        rest.exchange(
            "/api/v1/claims/" + claimId,
            HttpMethod.GET,
            new HttpEntity<>(null, authHeaders()),
            String.class);
    assertThat(sameTenant.getStatusCode().value())
        .as("同租户读不到 → 后面的 404 不是租户边界造成的，反例无效：%s", sameTenant.getBody())
        .isEqualTo(200);

    ResponseEntity<String> crossTenant =
        rest.exchange(
            "/api/v1/claims/" + claimId,
            HttpMethod.GET,
            new HttpEntity<>(null, headersFor(1L)),
            String.class);
    assertThat(crossTenant.getStatusCode().value())
        .as("跨租户必须 404（防枚举），实际 %s，body=%s", crossTenant.getStatusCode(), crossTenant.getBody())
        .isEqualTo(404);
    assertThat(crossTenant.getBody())
        .as("错误码须为「查不到」；403 与 404 的差异会泄露该 ID 在别的租户存在")
        .contains("SC_CLAIM_NOT_FOUND");
    System.out.println("[E2E] 跨租户读 claimId=" + claimId + " → 404 SC_CLAIM_NOT_FOUND");
  }

  private JsonNode allocate(long claimId) {
    HttpHeaders h = authHeaders();
    ResponseEntity<String> resp =
        rest.exchange(
            "/api/v1/claims/" + claimId + "/allocate",
            HttpMethod.POST,
            new HttpEntity<>(null, h),
            String.class);
    return body(resp);
  }

  private JsonNode startPreExam(long claimId) {
    HttpHeaders h = authHeaders();
    h.set("X-Automated", "true");
    ResponseEntity<String> resp =
        rest.exchange(
            "/api/v1/claims/" + claimId + "/pre-exam/start",
            HttpMethod.POST,
            new HttpEntity<>(null, h),
            String.class);
    return body(resp);
  }

  private JsonNode executeDecision(long claimId) {
    HttpHeaders h = authHeaders();
    h.set("Idempotency-Key", UUID.randomUUID().toString());
    String body =
        "{\"graphVersion\":\"v1\",\"ruleVersion\":\"v1\",\"modelVersion\":\"\",\"promptVersion\":\"\",\"catalogVersion\":\"\"}";
    ResponseEntity<String> resp =
        rest.exchange(
            "/api/v1/claims/" + claimId + "/decision/execute",
            HttpMethod.POST,
            new HttpEntity<>(body, h),
            String.class);
    return body(resp);
  }

  /**
   * 报案 1:0..N 赔付的端到端贯通：建案产出报案号 → 查报案详情 → 追加第二笔 → 重放不新增 → 反例（400/404）。
   *
   * <p><b>为什么必须在真库真 HTTP 上跑一遍</b>：报案号/赔案号由聚合派生、跨两张表同事务写入、报告清单靠 {@code findByReportId} 反查、{@code
   * report_id} 是 {@code NOT NULL} 外键列——这四件事任何一件在内存替身里都是"天然成立"的。 只有真跑才知道：INSERT 的列是否与活库对齐、追加案的
   * {@code report_id} 是否真的写进去了（写空即 1054 或挂不到报案下）、 幂等重放是否真的复用了赔案而不是又建一笔。
   *
   * <p>反例部分刻意验"错误码"而非只验状态码：400 与 404 都可能由通用参数校验触发，只有 {@code errorCode}
   * 能证明拒绝发生在"报案维度"这一层（而不是请求体解析失败）。
   */
  @Test
  @DisplayName("报案详情与追加赔案：1:0..N 贯通 + 幂等重放不新增 + 400/404 反例")
  void reportDetailAndAppendCaseEndToEnd() throws Exception {
    String submit =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "policyNo", "E2E-POLICY-0001",
                "planId", 9001,
                "insuredIdHash", "e2e-insured-hash-0001",
                "outInsureTime", "2026-11-01T00:00:00Z",
                "claimType", "OUTPATIENT",
                "items", List.of(java.util.Map.of("itemName", "诊查费", "amount", 100.00))));
    JsonNode first = body(exchange(HttpMethod.POST, "/api/v1/claims", submit, idempotentHeaders()));

    String claimNo = first.get("claimNo").asText();
    String reportNo = first.hasNonNull("reportNo") ? first.get("reportNo").asText() : null;
    assertThat(reportNo).as("建案响应必须带回报案号（否则前端拿不到追加入口所需的键）").isNotNull();
    assertThat(reportNo).as("报案号前缀 RP").startsWith("RP");
    assertThat(claimNo).as("赔案号前缀 CL").startsWith("CL");
    assertThat(reportNo).as("两套号互不推导：同一笔的第一案也不能让报案号等于赔案号").isNotEqualTo(claimNo);
    System.out.println("[E2E] 建案 claimNo=" + claimNo + " reportNo=" + reportNo);

    // 1) 报案详情：刚建案即有 1 笔赔案
    JsonNode detail = body(exchange(HttpMethod.GET, "/api/v1/claims/reports/" + reportNo, null));
    assertThat(detail.get("reportNo").asText()).isEqualTo(reportNo);
    assertThat(detail.get("status").asText()).isEqualTo("REPORTED");
    assertThat(detail.get("caseCount").asInt()).isEqualTo(1);
    assertThat(detail.get("allCasesFinished").asBoolean()).as("在办赔案未终态，不能显示已完成").isFalse();
    // 报案头持有事故事实：追加案的一切上下文都从这里继承
    assertThat(detail.get("policyNo").asText()).isEqualTo("E2E-POLICY-0001");
    assertThat(detail.get("planId").asLong()).isEqualTo(9001);
    assertThat(detail.get("cases").get(0).get("claimNo").asText()).isEqualTo(claimNo);

    // 2) 追加第二笔（只带赔付维度，不带任何保单维度）
    String append =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "invoiceCode", "0440" + (System.nanoTime() % 10000),
                "invoiceNo", String.valueOf(System.nanoTime()),
                "items", List.of(java.util.Map.of("itemName", "治疗费", "amount", 200.00))));
    String appendKey = UUID.randomUUID().toString();
    ResponseEntity<String> appendedResp =
        exchange(
            HttpMethod.POST,
            "/api/v1/claims/reports/" + reportNo + "/cases",
            append,
            idempotentHeaders(appendKey));
    assertThat(appendedResp.getStatusCode().value())
        .as("追加成功必须是 201，body=%s", appendedResp.getBody())
        .isEqualTo(201);
    JsonNode appended = body(appendedResp);
    String secondClaimNo = appended.get("claimNo").asText();
    assertThat(secondClaimNo).startsWith("CL").isNotEqualTo(claimNo);
    assertThat(appended.get("reportNo").asText()).as("追加案必须回报同一个报案号").isEqualTo(reportNo);
    assertThat(objectMapper.readTree(appendedResp.getBody()).get("code").asInt())
        .as("信封 code 必须与 HTTP 201 一致（API 规范 §3.1）")
        .isEqualTo(201);
    System.out.println("[E2E] 追加赔案 claimNo=" + secondClaimNo);

    // 3) 同一幂等键重放 → 返回同一笔赔案，盘点仍为 2
    JsonNode replayed =
        body(
            exchange(
                HttpMethod.POST,
                "/api/v1/claims/reports/" + reportNo + "/cases",
                append,
                idempotentHeaders(appendKey)));
    assertThat(replayed.get("claimNo").asText())
        .as("重放必须复用首笔追加案，否则同一张发票会赔两次")
        .isEqualTo(secondClaimNo);
    assertThat(
            body(exchange(HttpMethod.GET, "/api/v1/claims/reports/" + reportNo, null))
                .get("caseCount")
                .asInt())
        .as("重放不得新增赔案")
        .isEqualTo(2);

    // 4) 400：请求体带保单维度字段 → CONTEXT_MISMATCH（不静默忽略）
    String illegal =
        objectMapper.writeValueAsString(
            java.util.Map.of(
                "policyNo",
                "E2E-POLICY-0001",
                "items",
                List.of(java.util.Map.of("itemName", "诊查费", "amount", 1.00))));
    ResponseEntity<String> rejected =
        exchange(
            HttpMethod.POST,
            "/api/v1/claims/reports/" + reportNo + "/cases",
            illegal,
            idempotentHeaders());
    assertThat(rejected.getStatusCode().value()).as("携带保单维度字段即 400").isEqualTo(400);
    // errorCode 在 ProblemDetail 里嵌在 data 下（root 只有 success/code/message/timestamp）
    assertThat(objectMapper.readTree(rejected.getBody()).path("data").path("errorCode").asText())
        .as("必须是报案上下文不匹配，而不是通用参数校验失败 —— 两者都是 400")
        .isEqualTo("SC_CLAIM_REPORT_CONTEXT_MISMATCH");

    // 5) 404：不存在的报案号（与跨租户同号同一口径，不泄露存在性）
    ResponseEntity<String> missing =
        exchange(HttpMethod.GET, "/api/v1/claims/reports/RP20260101ZZZZZZZZ9999", null);
    assertThat(missing.getStatusCode().value()).isEqualTo(404);
    assertThat(objectMapper.readTree(missing.getBody()).path("data").path("errorCode").asText())
        .as("报案不存在与跨租户同号同一口径（防枚举），不走通用 NOT_FOUND")
        .isEqualTo("SC_CLAIM_REPORT_NOT_FOUND");
  }
}
