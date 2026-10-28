package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.DecisionMetricsPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeContext;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeOutcome;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.model.decision.DecisionNodeStatus;
import com.bone.claim.domain.model.decision.DecisionResult;
import com.bone.claim.domain.model.decision.DecisionTier;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * AI 决策溯源可自证：模型 / 提示词版本必须能在执行记录里定位。
 *
 * <p>背景（真实债务，非假想）：V5 {@code claim_decision_execution.model_version} 注释写明"回放与复算必需"， 但该字段取值范围来自 HTTP
 * 调用方的可选入参，缺省即空串——AI 真跑过，溯源却一片空白。此处锁定修正后的语义：
 *
 * <ul>
 *   <li>调用方漏传 → 用 AI 节点自报的真实上游模型 / 提示词契约版本兜底；
 *   <li>调用方显式传入 → <b>不被覆盖</b>（回归基线固定版本号时不得被自报值改写）；
 *   <li>AI 节点未自报 → 保持为空，<b>绝不臆造</b>版本号；
 *   <li>执行终结后不得改写版本快照（防止审计结论被追溯性篡改）。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionAiTraceabilityTest {

  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 42L;

  @Mock private DecisionExecutionApplicationService decisionExecutionApplicationService;
  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private TenantPort tenantPort;

  private ArgumentCaptor<DecisionExecution> persisted;

  @BeforeEach
  void setUp() {
    persisted = ArgumentCaptor.forClass(DecisionExecution.class);
    when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    when(decisionExecutionApplicationService.findActive(anyLong())).thenReturn(null);
    when(claimCaseApplicationService.getClaimAggregate(anyLong())).thenReturn(preExamClaim());
  }

  @Test
  @DisplayName("调用方漏传版本 → 执行记录用 AI 节点自报的真实模型 / 提示词版本兜底")
  void backfillsFromAiNodeWhenCallerOmitsVersions() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(handlers("hunyuan-turbo", "claim-assess-v2")).execute(CLAIM_ID, versions("", ""));

    assertThat(result.status()).isNotBlank();
    DecisionExecution execution = capturePersisted();
    // 关键判据：AI 参与了，执行记录就必须能定位到"当时那套模型 + 提示词"
    assertThat(execution.getModelVersion()).isEqualTo("hunyuan-turbo");
    assertThat(execution.getPromptVersion()).isEqualTo("claim-assess-v2");
  }

  @Test
  @DisplayName("调用方显式传入版本 → 不被自报值覆盖（负向对照，证明回填只补空）")
  void explicitVersionsWinOverSelfReported() {
    executor(handlers("hunyuan-turbo", "claim-assess-v2"))
        .execute(CLAIM_ID, versions("pinned-model", "pinned-prompt"));

    DecisionExecution execution = capturePersisted();
    assertThat(execution.getModelVersion()).isEqualTo("pinned-model");
    assertThat(execution.getPromptVersion()).isEqualTo("pinned-prompt");
  }

  @Test
  @DisplayName("AI 节点未自报版本 → 保持空，绝不臆造")
  void leavesBlankWhenAiReportsNothing() {
    executor(handlers(null, null)).execute(CLAIM_ID, versions("", ""));

    DecisionExecution execution = capturePersisted();
    assertThat(execution.getModelVersion()).isBlank();
    assertThat(execution.getPromptVersion()).isBlank();
  }

  @Test
  @DisplayName("执行终结后拒绝回填版本（版本快照属执行期事实，不可追溯改写）")
  void backfillRejectedAfterCompletion() {
    DecisionExecution execution =
        DecisionExecution.start(9L, TENANT_ID, "trace-x", CLAIM_ID, "g1", "r1", "", "", "c1");
    execution.complete(DecisionResult.MANUAL_REQUIRED, DecisionTier.MANUAL, null, false);

    assertThatThrownBy(() -> execution.backfillAiVersions("m-late", "p-late"))
        .isInstanceOf(DomainException.class);
    assertThat(execution.getModelVersion()).isBlank();
  }

  // ===================== 夹具 =====================

  private DecisionExecution capturePersisted() {
    verify(decisionExecutionApplicationService).persist(persisted.capture());
    return persisted.getValue();
  }

  private DecisionGraphExecutor executor(List<DecisionNodeHandler> handlers) {
    return new DecisionGraphExecutor(
        decisionExecutionApplicationService,
        claimCaseApplicationService,
        tenantPort,
        DecisionMetricsPort.NOOP,
        handlers);
  }

  private static DecisionGraphExecutor.DecisionVersions versions(
      String modelVersion, String promptVersion) {
    return new DecisionGraphExecutor.DecisionVersions(
        "g1", "r1", modelVersion, promptVersion, "c1");
  }

  /** 五节点全成功链；AI 节点按需自报模型 / 提示词版本（null = 不自报）。 */
  private static List<DecisionNodeHandler> handlers(String aiModel, String aiPromptVersion) {
    Map<String, Object> aiOutput = new HashMap<>();
    aiOutput.put("rejectSuggested", false);
    if (aiModel != null) {
      aiOutput.put("aiModel", aiModel);
    }
    if (aiPromptVersion != null) {
      aiOutput.put("aiPromptVersion", aiPromptVersion);
    }

    return List.of(
        node(
            DecisionGraph.EVIDENCE_LOAD,
            DecisionNodeStatus.SUCCEEDED,
            Map.of(
                "hasEvidence",
                true,
                "evidenceParsed",
                true,
                "ocrConfidence",
                new BigDecimal("0.95")),
            null),
        node(
            DecisionGraph.RULE_CHECK,
            DecisionNodeStatus.SUCCEEDED,
            Map.of(
                "ruleConflict",
                false,
                "fraudGatePassed",
                true,
                "riskHit",
                false,
                "ruleConfidence",
                new BigDecimal("0.90")),
            new BigDecimal("0.90")),
        node(
            DecisionGraph.AI_ASSESS,
            DecisionNodeStatus.SUCCEEDED,
            aiOutput,
            new BigDecimal("0.93")),
        node(
            DecisionGraph.ADJUST,
            DecisionNodeStatus.SUCCEEDED,
            Map.of("amount", new BigDecimal("300.00")),
            null),
        node(DecisionGraph.ARBITRATE, DecisionNodeStatus.SUCCEEDED, Map.of(), null));
  }

  private static DecisionNodeHandler node(
      String name, DecisionNodeStatus status, Map<String, Object> output, BigDecimal confidence) {
    return new DecisionNodeHandler() {
      @Override
      public String nodeName() {
        return name;
      }

      @Override
      public DecisionNodeOutcome execute(DecisionNodeContext context) {
        return new DecisionNodeOutcome(status, output, confidence, "摘要", "OK");
      }
    };
  }

  private static ClaimCase preExamClaim() {
    ClaimCase claim =
        ClaimCase.draft(
            CLAIM_ID,
            TENANT_ID,
            "P-1",
            7L,
            "hash-1",
            "OUTPATIENT",
            "INV-1",
            "INVNO-1",
            Instant.now(),
            null,
            "v1",
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
}
