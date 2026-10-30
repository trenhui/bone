package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.handler.AdjustNodeHandler;
import com.bone.claim.application.handler.AiAssessNodeHandler;
import com.bone.claim.application.handler.ArbitrateNodeHandler;
import com.bone.claim.application.handler.EvidenceLoadNodeHandler;
import com.bone.claim.application.handler.RuleCheckNodeHandler;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionMetricsPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.infrastructure.ai.LocalAiGatewayPortAdapter;
import com.bone.claim.infrastructure.forensics.LocalVisualForensicsPortAdapter;
import com.bone.claim.infrastructure.fraud.LocalGraphFraudPortAdapter;
import com.bone.claim.infrastructure.knowledge.LocalKnowledgeRetrievalPortAdapter;
import com.bone.claim.infrastructure.notify.LocalNotificationPortAdapter;
import com.bone.claim.infrastructure.rule.LocalRuleEnginePortAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A4 决策图功能测试（贯穿式，非 mock 处理器）：证明 5 个节点处理器真实注入后， 决策图能按企业场景产出 AUTO_APPROVE / MANUAL_REQUIRED /
 * NEED_MORE_EVIDENCE， 而非 {@code DecisionGraphExecutorTest} 所覆盖的"零处理器永远转人工"假绿。
 *
 * <p>装配策略：真实处理器 + 本地桩端口（无 DB 依赖），仅 {@link ClaimContextPort} 用内存假实现驱动场景。 不启 Spring，避免 SDK/H2
 * 集成脆弱性——本测试只验证"图接线后真能决策"这一此前缺失的判别力。
 */
@ExtendWith(MockitoExtension.class)
class DecisionGraphFunctionalTest {

  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 42L;

  @Mock private ClaimCaseApplicationService claimSvc;
  @Mock private DecisionExecutionApplicationService execSvc;

  private ClaimCase preExamClaim;
  private ArgumentCaptor<DecisionExecution> captured;

  @BeforeEach
  void setUp() {
    preExamClaim =
        ClaimCase.draft(
            1001L,
            TENANT_ID,
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "04400001",
            "12345678",
            Instant.parse("2026-03-12T10:00:00Z"),
            "B1",
            "v3",
            List.of(
                ClaimItem.of(
                    1L,
                    1L,
                    1001L,
                    "门诊费",
                    "C1",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    new BigDecimal("300.00"),
                    new BigDecimal("300.00"))));
    preExamClaim.accept();
    preExamClaim.allocateToPreExam("op-1");
    captured = ArgumentCaptor.forClass(DecisionExecution.class);
    when(claimSvc.getClaimAggregate(CLAIM_ID)).thenReturn(preExamClaim);
    when(execSvc.findActive(anyLong())).thenReturn(null);
  }

  /**
   * 场景 A：证据齐 + OCR 0.95 + 风控通过 + AI 0.93 + 核责/反欺诈/知识三能力均真实可用且干净 → 自动通过。
   *
   * <p>注意：{@code AUTO_APPROVE} 仅在 C2 核责引擎与 E1/E2 反欺诈能力都<b>真实可用</b>（{@code available=true}）时可达；
   * 本场景显式注入三路"已接入"适配器，证明闸逻辑正确、图真能自动决策，而非永远转人工。
   */
  @Test
  @DisplayName("干净案件（核责 + 反欺诈 + 知识能力均可用）→ AUTO_APPROVE（验证闸逻辑，非永远转人工）")
  void cleanClaimAutoApproves() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi(),
                availableKnowledge(),
                fraudAvailableVisual(),
                fraudAvailableGraph(),
                readyRule())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(result.traceCount()).isEqualTo(5);
    assertThat(result.status()).isEqualTo("SUCCESS");
    verify(execSvc).persist(captured.capture());
    assertThat(captured.getValue().getAiConfidence()).isEqualByComparingTo(new BigDecimal("0.93"));
  }

  /** 场景 B：金额 ≥1 万 → 大额不自动通过（进复核档 MANUAL_REQUIRED）。 */
  @Test
  @DisplayName("大额（≥1万） → MANUAL_REQUIRED（复核档，不自动通过）")
  void largeAmountGoesToReview() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("20000.00")),
                enabledAi())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
  }

  /**
   * 场景 C：AI 网关故障 → 降级纯规则路径（FALLBACK），规则置信度 0.90 仍自动通过。
   *
   * <p>反欺诈能力可用（{@code available=true}）前提下，AI 降级不影响自动通过；fail-closed 加固只影响"反欺诈能力本身缺失"的态（见场景 F）。
   */
  @Test
  @DisplayName("AI 不可用 → FALLBACK 降级，三能力可用时仍 AUTO_APPROVE（规则路径置信度兜底）")
  void aiUnavailableFallsBackToRulePath() {
    LocalAiGatewayPortAdapter ai = new LocalAiGatewayPortAdapter();
    ai.setEnabled(false);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                ai,
                availableKnowledge(),
                fraudAvailableVisual(),
                fraudAvailableGraph(),
                readyRule())
            .execute(CLAIM_ID, versions());

    assertThat(result.status()).isEqualTo("FALLBACK");
    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    verify(execSvc).persist(captured.capture());
    assertThat(captured.getValue().getAiConfidence()).isEqualByComparingTo(new BigDecimal("0.90"));
  }

  /** 场景 D：图计算反欺诈命中（能力可用且确实命中）→ 强制转人工（RISK_HIT / 闸不过）。 */
  @Test
  @DisplayName("反欺诈命中（图计算，能力可用） → MANUAL_REQUIRED")
  void fraudHitEscalatesToManual() {
    GraphFraudPort graph = claimId -> new GraphFraudPort.FraudResult(true, "cluster-9", true);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi(),
                availableKnowledge(),
                fraudAvailableVisual(),
                graph,
                readyRule())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
  }

  /** 场景 E：证据缺失 → 缺件流转（NEED_MORE_EVIDENCE，区别于能力缺失）。 */
  @Test
  @DisplayName("证据缺失 → NEED_MORE_EVIDENCE（与能力缺失 MANUAL_REQUIRED 区分）")
  void missingEvidence() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(ctx(false, false, null, false, new BigDecimal("300.00")), enabledAi())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("NEED_MORE_EVIDENCE");
  }

  /**
   * 场景 F（fail-closed 加固证伪）：反欺诈增强能力未集成（本地桩 {@code available=false}），即便案件本身干净、核责与知识能力都已接入，
   * 也<b>不得</b>自动通过——必须升级人工。
   *
   * <p>这是对此前"本地桩恒返回 tampered=false/hit=false 让反欺诈闸恒为真"假绿缺陷的针对性证伪： 删除 {@code RuleCheckNodeHandler} 中
   * {@code capabilitiesPresent} 的 fail-closed 判定，本场景将从 AUTO_APPROVE 变回， 证明该判定是真实生效的护栏。
   */
  @Test
  @DisplayName("反欺诈能力缺失（E1/E2 本地桩）→ 即便干净也 MANUAL_REQUIRED（fail-closed，杜绝静默放行）")
  void fraudCapabilityMissingEscalatesToManual() {
    // 规则 + 知识能力都注入"已接入"，隔离变量：本场景的 MANUAL_REQUIRED 纯粹来自反欺诈闸缺失
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi(),
                availableKnowledge(),
                new LocalVisualForensicsPortAdapter(),
                new LocalGraphFraudPortAdapter(),
                readyRule())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
  }

  /**
   * 场景 G（2026-10-29 语义修正证伪）：知识检索能力未集成（{@code available=false}）→ 临床合理性<b>无法判定</b>， 落点是 {@code
   * MANUAL_REQUIRED}（转人工队列），<b>不是</b> {@code SUGGEST_REJECT}。
   *
   * <p>修正前此处断言 {@code SUGGEST_REJECT}：{@code AiAssessNodeHandler} 把 {@code !knowledgeConfirmed}
   * 直接并进 {@code rejectSuggested}，于是"知识库没接入"被落成对客户的不利决定—— 会触发 3
   * 日书面通知、五要素通知书、申诉通道与监管投诉计量，而它本应只是"进人工队列"。
   *
   * <p>本场景是全套能力就绪、仅知识缺失时唯一的免自动放款护栏：把 {@code capabilityMissing} 从决策图移除， 本场景将变回 {@code
   * AUTO_APPROVE}（自动放款），证明该护栏真实生效。
   */
  @Test
  @DisplayName("知识检索能力缺失 → MANUAL_REQUIRED（无法判定≠拒赔，且禁止自动放行）")
  void knowledgeCapabilityMissingEscalatesToManual() {
    // 规则 + 反欺诈能力全可用 + 本地知识桩（available=false）：隔离"知识缺失"单变量
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi(),
                new LocalKnowledgeRetrievalPortAdapter(),
                fraudAvailableVisual(),
                fraudAvailableGraph(),
                readyRule())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
  }

  /** 场景 H：知识检索已接入且判定临床不合理 → 系统建议拒赔（SUGGEST_REJECT，转人工，有依据的不利结论）。 */
  @Test
  @DisplayName("知识检索判定临床不合理（能力可用）→ SUGGEST_REJECT")
  void knowledgeClinicallyUnreasonable() {
    KnowledgeRetrievalPort knowledge =
        claimId ->
            new KnowledgeRetrievalPort.KnowledgeResult(
                false, BigDecimal.ZERO, "E3-unreasonable", true);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi(),
                knowledge,
                fraudAvailableVisual(),
                fraudAvailableGraph(),
                readyRule())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("SUGGEST_REJECT");
  }

  /**
   * 场景 I（核责规则并入硬闸的证伪）：规则引擎仍是桩（{@code available=false}），知识/反欺诈能力均已接入且案件干净 → <b>不得</b>自动通过。核责没跑过时的
   * {@code ruleConflict=false} 只是默认值，不是"已核责且无冲突"。
   *
   * <p>把 {@code RuleCheckNodeHandler} 中的 {@code ruleAvailable} 从硬闸条件移除，本场景将变回 {@code AUTO_APPROVE}。
   */
  @Test
  @DisplayName("核责规则未真实执行（C2 桩）→ 即便其余能力可用也 MANUAL_REQUIRED（没核责就不放款）")
  void ruleCapabilityMissingEscalatesToManual() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi(),
                availableKnowledge(),
                fraudAvailableVisual(),
                fraudAvailableGraph(),
                new LocalRuleEnginePortAdapter())
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
  }

  // ===================== 装配 =====================

  /**
   * 默认装配：知识检索 + 反欺诈 + 规则引擎均用本地桩（{@code available=false}），即"C2/E3/E1/E2 全未集成"的真实运行时态。 此时干净案件亦
   * fail-closed 转人工（见场景 F/G），证明无能力即不自动决策。
   */
  private DecisionGraphExecutor executor(ClaimContextPort ctxPort, AiGatewayPort ai) {
    return executor(
        ctxPort,
        ai,
        new LocalKnowledgeRetrievalPortAdapter(),
        new LocalVisualForensicsPortAdapter(),
        new LocalGraphFraudPortAdapter(),
        new LocalRuleEnginePortAdapter());
  }

  /** 仅覆盖知识适配器（规则/反欺诈仍本地桩），用于隔离"知识能力"单变量。 */
  private DecisionGraphExecutor executor(
      ClaimContextPort ctxPort, AiGatewayPort ai, KnowledgeRetrievalPort knowledge) {
    return executor(
        ctxPort,
        ai,
        knowledge,
        new LocalVisualForensicsPortAdapter(),
        new LocalGraphFraudPortAdapter(),
        new LocalRuleEnginePortAdapter());
  }

  /** 注入"知识 + 反欺诈"已接入的适配器，规则引擎仍本地桩（用于证明规则路缺失也能挡住自动放行）。 */
  private DecisionGraphExecutor executor(
      ClaimContextPort ctxPort,
      AiGatewayPort ai,
      KnowledgeRetrievalPort knowledge,
      VisualForensicsPort visual,
      GraphFraudPort graph) {
    return executor(ctxPort, ai, knowledge, visual, graph, new LocalRuleEnginePortAdapter());
  }

  /** 显式装配：可注入"能力已接入"的知识/反欺诈/规则适配器，隔离单变量验证闸逻辑。 */
  private DecisionGraphExecutor executor(
      ClaimContextPort ctxPort,
      AiGatewayPort ai,
      KnowledgeRetrievalPort knowledge,
      VisualForensicsPort visual,
      GraphFraudPort graph,
      RuleEnginePort rule) {
    List<DecisionNodeHandler> handlers =
        List.of(
            new EvidenceLoadNodeHandler(ctxPort),
            new RuleCheckNodeHandler(ctxPort, rule, visual, graph),
            new AiAssessNodeHandler(ctxPort, ai, knowledge, new LocalNotificationPortAdapter()),
            new AdjustNodeHandler(ctxPort, rule),
            new ArbitrateNodeHandler());
    TenantPort tenantPort = () -> TENANT_ID;
    return new DecisionGraphExecutor(
        execSvc, claimSvc, tenantPort, DecisionMetricsPort.NOOP, handlers);
  }

  /** 规则引擎已接入（C2）：真实执行核责、无冲突、验真通过、规则置信度 0.90。 */
  private static RuleEnginePort readyRule() {
    return new RuleEnginePort() {
      @Override
      public RuleEnginePort.RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
        return new RuleEnginePort.RuleCheckResult(true, false, false, true, new BigDecimal("0.90"));
      }

      @Override
      public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx) {
        return ctx.claimAmount();
      }
    };
  }

  /** 知识检索能力已接入（E3 GraphRAG）：临床合理、能力已执行。 */
  private static KnowledgeRetrievalPort availableKnowledge() {
    return claimId ->
        new KnowledgeRetrievalPort.KnowledgeResult(true, BigDecimal.ZERO, "integrated-E3", true);
  }

  /** 反欺诈能力已接入（E1 视觉）：未篡改、能力已执行。 */
  private static VisualForensicsPort fraudAvailableVisual() {
    return claimId -> new VisualForensicsPort.ForensicsResult(false, BigDecimal.ZERO, true);
  }

  /** 反欺诈能力已接入（E2 图计算）：未命中、能力已执行。 */
  private static GraphFraudPort fraudAvailableGraph() {
    return claimId -> new GraphFraudPort.FraudResult(false, null, true);
  }

  private static AiGatewayPort enabledAi() {
    return new LocalAiGatewayPortAdapter();
  }

  private ClaimContextPort ctx(
      boolean hasEvidence,
      boolean parsed,
      BigDecimal ocr,
      boolean verifyPassed,
      BigDecimal amount) {
    return claimId ->
        new ClaimContextPort.ClaimContextView(
            claimId, "OUTPATIENT", amount, hasEvidence, parsed, ocr, verifyPassed);
  }

  private static DecisionGraphExecutor.DecisionVersions versions() {
    return new DecisionGraphExecutor.DecisionVersions("g1", "r1", "m1", "p1", "c1");
  }
}
