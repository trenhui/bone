package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.handler.AdjustNodeHandler;
import com.bone.claim.application.handler.AiAssessNodeHandler;
import com.bone.claim.application.handler.ArbitrateNodeHandler;
import com.bone.claim.application.handler.EvidenceLoadNodeHandler;
import com.bone.claim.application.handler.RuleCheckNodeHandler;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionMetricsPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.application.port.out.TriageLimitsPort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.config.AiRolloutStage;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.TriageLimits;
import com.bone.claim.infrastructure.ai.LocalAiGatewayPortAdapter;
import com.bone.claim.infrastructure.notify.LocalNotificationPortAdapter;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * A3-1「分桶必需」对照实验（v2 业界校准 A0/A3）。
 *
 * <p>要证明的不是「又有了一道护栏」，而是两件可证伪的事：
 *
 * <ol>
 *   <li><b>摘除模型即断流</b>：CANARY / AUTOMATED 档下 LLM 节点 FALLBACK 时，纯规则路径不得再产出 AUTO_APPROVE，必须
 *       MANUAL_REQUIRED，并把原因码落进 triage_reason 供回放；
 *   <li><b>零行为变更</b>：SHADOW / ASSISTED 档、未注入阶段机的测试基座、以及模型正常参与时， 结论与改动前逐位一致（AUTO_APPROVE /
 *       FALLBACK）——不为追 AI 原生牺牲默认可用性。
 * </ol>
 *
 * <p>装配与 {@link DecisionGraphFunctionalTest} 场景 C 同源：真实 5 节点处理器 + 本地桩端口， 唯一变量是「放量档」与「AI
 * 是否可用」。变异验证：把 {@link AiRolloutStage#requiresModelParticipation()} 改成恒 false，本类 CANARY / AUTOMATED
 * 两个用例必须转红，其余用例仍绿——证明判据有判别力而非恒真。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionGraphExecutorModelParticipationTest {

  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 42L;
  private static final String REASON = "MODEL_ABSENT_AUTO_STAGE";

  @Mock private ClaimCaseApplicationService claimSvc;
  @Mock private DecisionExecutionApplicationService execSvc;
  @Mock private AiRolloutApplicationService rollout;

  private ArgumentCaptor<DecisionExecution> persisted;

  @BeforeEach
  void setUp() {
    persisted = ArgumentCaptor.forClass(DecisionExecution.class);
    when(claimSvc.getClaimAggregate(CLAIM_ID)).thenReturn(preExamClaim());
    when(execSvc.findActive(anyLong())).thenReturn(null);
  }

  @Test
  @DisplayName("CANARY + 模型缺席 → MANUAL_REQUIRED，原因码可回放（摘除即断流）")
  void canaryRequiresModelParticipation() {
    when(rollout.modelParticipationRequired(anyLong(), anyLong(), anyString())).thenReturn(true);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(AiRolloutStage.CANARY, false).execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
    assertThat(result.status()).isEqualTo("FALLBACK");
    verify(execSvc).persist(persisted.capture());
    assertThat(persisted.getValue().getTriageReason()).isEqualTo(REASON);
  }

  @Test
  @DisplayName("AUTOMATED + 模型缺席 → 同样 MANUAL_REQUIRED（全量档不放宽）")
  void automatedRequiresModelParticipation() {
    when(rollout.modelParticipationRequired(anyLong(), anyLong(), anyString())).thenReturn(true);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(AiRolloutStage.AUTOMATED, false).execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
    verify(execSvc).persist(persisted.capture());
    assertThat(persisted.getValue().getTriageReason()).isEqualTo(REASON);
  }

  @Test
  @DisplayName("对照：SHADOW（默认档）+ 模型缺席 → 仍 AUTO_APPROVE / FALLBACK，零行为变更")
  void shadowKeepsLegacyFallback() {
    when(rollout.modelParticipationRequired(anyLong(), anyLong(), anyString())).thenReturn(false);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(AiRolloutStage.SHADOW, false).execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(result.status()).isEqualTo("FALLBACK");
  }

  @Test
  @DisplayName("对照：ASSISTED（人工决策档）→ 仍 AUTO_APPROVE / FALLBACK")
  void assistedKeepsLegacyFallback() {
    when(rollout.modelParticipationRequired(anyLong(), anyLong(), anyString())).thenReturn(false);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(AiRolloutStage.ASSISTED, false).execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(result.status()).isEqualTo("FALLBACK");
  }

  @Test
  @DisplayName("对照：模型正常参与（CANARY）→ AUTO_APPROVE，收紧只针对模型缺席")
  void canaryWithModelPresentAutoApproves() {
    when(rollout.modelParticipationRequired(anyLong(), anyLong(), anyString())).thenReturn(true);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(AiRolloutStage.CANARY, true).execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(result.status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("对照：未注入阶段机（6 参测试基座）→ 保持旧行为 AUTO_APPROVE")
  void noStageMachineKeepsLegacyFallback() {
    DecisionGraphExecutor legacy = executorWithoutStageMachine(false);

    DecisionGraphExecutor.DecisionExecutionResult result = legacy.execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(result.status()).isEqualTo("FALLBACK");
  }

  @Test
  @DisplayName("负向：阶段查询异常 → fail-closed 转人工，不得借异常放宽")
  void stageLookupFailureFailsClosed() {
    when(rollout.modelParticipationRequired(anyLong(), anyLong(), anyString()))
        .thenThrow(new IllegalStateException("db down"));

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(AiRolloutStage.CANARY, false).execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
    verify(execSvc).persist(persisted.capture());
    assertThat(persisted.getValue().getTriageReason()).isEqualTo(REASON);
  }

  @Test
  @DisplayName("不覆盖更精确原因码：大额复核档 + 模型缺席 → 原因码仍为金额档位")
  void doesNotOverwriteMorePreciseReason() {
    when(rollout.modelParticipationRequired(anyLong(), anyLong(), anyString())).thenReturn(true);

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(AiRolloutStage.CANARY, false, new BigDecimal("20000.00"))
            .execute(CLAIM_ID, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
    verify(execSvc).persist(persisted.capture());
    assertThat(persisted.getValue().getTriageReason()).isNotEqualTo(REASON);
  }

  // ===================== 装配 =====================

  private DecisionGraphExecutor executor(AiRolloutStage stage, boolean aiEnabled) {
    return executor(stage, aiEnabled, new BigDecimal("300.00"));
  }

  private DecisionGraphExecutor executor(
      AiRolloutStage stage, boolean aiEnabled, BigDecimal amount) {
    return executor(handlers(aiEnabled, amount), rollout);
  }

  private DecisionGraphExecutor executorWithoutStageMachine(boolean aiEnabled) {
    return new DecisionGraphExecutor(
        execSvc,
        claimSvc,
        (TenantPort) () -> TENANT_ID,
        DecisionMetricsPort.NOOP,
        triageLimits(),
        handlers(aiEnabled, new BigDecimal("300.00")));
  }

  private DecisionGraphExecutor executor(
      List<DecisionNodeHandler> handlers, AiRolloutApplicationService stageService) {
    return new DecisionGraphExecutor(
        execSvc,
        claimSvc,
        (TenantPort) () -> TENANT_ID,
        DecisionMetricsPort.NOOP,
        triageLimits(),
        handlers,
        stageService);
  }

  private static TriageLimitsPort triageLimits() {
    return tenantId -> TriageLimits.defaultResolution();
  }

  private List<DecisionNodeHandler> handlers(boolean aiEnabled, BigDecimal amount) {
    ClaimContextPort ctx =
        claimId ->
            new ClaimContextPort.ClaimContextView(
                claimId, "OUTPATIENT", amount, true, true, new BigDecimal("0.95"), true);
    LocalAiGatewayPortAdapter ai = new LocalAiGatewayPortAdapter();
    ai.setEnabled(aiEnabled);
    return List.of(
        new EvidenceLoadNodeHandler(ctx),
        new RuleCheckNodeHandler(ctx, readyRule(), availableVisual(), availableGraph()),
        new AiAssessNodeHandler(ctx, ai, availableKnowledge(), new LocalNotificationPortAdapter()),
        new AdjustNodeHandler(ctx, readyRule()),
        new ArbitrateNodeHandler());
  }

  private static RuleEnginePort readyRule() {
    return new RuleEnginePort() {
      @Override
      public RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
        return new RuleCheckResult(true, false, false, true, new BigDecimal("0.90"));
      }

      @Override
      public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx) {
        return ctx.claimAmount();
      }
    };
  }

  private static KnowledgeRetrievalPort availableKnowledge() {
    return claimId -> new KnowledgeRetrievalPort.KnowledgeResult(true, BigDecimal.ZERO, "ok", true);
  }

  private static VisualForensicsPort availableVisual() {
    return claimId -> new VisualForensicsPort.ForensicsResult(false, BigDecimal.ZERO, true);
  }

  private static GraphFraudPort availableGraph() {
    return claimId -> new GraphFraudPort.FraudResult(false, null, true);
  }

  private static ClaimCase preExamClaim() {
    ClaimCase claim =
        ClaimCase.draft(
            CLAIM_ID,
            TENANT_ID,
            "EG20260001",
            7L,
            "hash-abc",
            "OUTPATIENT",
            "04400001",
            "12345678",
            Instant.parse("2026-03-12T10:00:00Z"),
            "B1",
            "idem-key-test-0",
            "v3",
            List.of(
                ClaimItem.of(
                    1L,
                    1L,
                    CLAIM_ID,
                    "门诊费",
                    "C1",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    new BigDecimal("300.00"),
                    new BigDecimal("300.00"))));
    claim.accept();
    claim.allocateToPreExam("op-1");
    return claim;
  }

  private static DecisionGraphExecutor.DecisionVersions versions() {
    return new DecisionGraphExecutor.DecisionVersions("g1", "r1", "m1", "p1", "c1");
  }
}
