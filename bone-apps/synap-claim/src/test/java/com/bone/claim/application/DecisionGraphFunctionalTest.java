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
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.TenantPort;
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

  /** 场景 A：证据齐 + OCR 0.95 + 风控通过 + AI 0.93 → 自动通过。 */
  @Test
  @DisplayName("干净案件 → AUTO_APPROVE（验证图真能自动决策，非永远转人工）")
  void cleanClaimAutoApproves() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi())
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

  /** 场景 C：AI 网关故障 → 降级纯规则路径（FALLBACK），规则置信度 0.90 仍自动通过。 */
  @Test
  @DisplayName("AI 不可用 → FALLBACK 降级，仍 AUTO_APPROVE（规则路径置信度兜底）")
  void aiUnavailableFallsBackToRulePath() {
    LocalAiGatewayPortAdapter ai = new LocalAiGatewayPortAdapter();
    ai.setEnabled(false);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")), ai)
            .execute(CLAIM_ID, versions());

    assertThat(result.status()).isEqualTo("FALLBACK");
    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    verify(execSvc).persist(captured.capture());
    assertThat(captured.getValue().getAiConfidence()).isEqualByComparingTo(new BigDecimal("0.90"));
  }

  /** 场景 D：图计算反欺诈命中 → 强制转人工。 */
  @Test
  @DisplayName("反欺诈命中（图计算） → MANUAL_REQUIRED（RISK_HIT）")
  void fraudHitEscalatesToManual() {
    GraphFraudPort graph = claimId -> new GraphFraudPort.FraudResult(true, "cluster-9");

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(
                ctx(true, true, new BigDecimal("0.95"), true, new BigDecimal("300.00")),
                enabledAi(),
                graph)
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

  // ===================== 装配 =====================

  private DecisionGraphExecutor executor(ClaimContextPort ctxPort, AiGatewayPort ai) {
    return executor(ctxPort, ai, new LocalGraphFraudPortAdapter());
  }

  private DecisionGraphExecutor executor(
      ClaimContextPort ctxPort, AiGatewayPort ai, GraphFraudPort graph) {
    List<DecisionNodeHandler> handlers =
        List.of(
            new EvidenceLoadNodeHandler(ctxPort),
            new RuleCheckNodeHandler(
                ctxPort,
                new LocalRuleEnginePortAdapter(),
                new LocalVisualForensicsPortAdapter(),
                graph),
            new AiAssessNodeHandler(
                ctxPort,
                ai,
                new LocalKnowledgeRetrievalPortAdapter(),
                new LocalNotificationPortAdapter()),
            new AdjustNodeHandler(ctxPort, new LocalRuleEnginePortAdapter()),
            new ArbitrateNodeHandler());
    TenantPort tenantPort = () -> TENANT_ID;
    return new DecisionGraphExecutor(execSvc, claimSvc, tenantPort, handlers);
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
