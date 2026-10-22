package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.time.Instant;
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

/** A3 决策图执行器单测：节点顺序 / LLM 降级 / 确定性失败中止 / 能力缺失 fail-safe。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionGraphExecutorTest {

  @Mock private DecisionExecutionApplicationService decisionExecutionApplicationService;
  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private TenantPort tenantPort;

  private ArgumentCaptor<DecisionExecution> persisted;

  @BeforeEach
  void setUp() {
    persisted = ArgumentCaptor.forClass(DecisionExecution.class);
    when(tenantPort.currentTenantId()).thenReturn(42L);
    when(decisionExecutionApplicationService.findActive(anyLong())).thenReturn(null);
    when(claimCaseApplicationService.getClaimAggregate(anyLong())).thenReturn(preExamClaim());
  }

  private static ClaimCase preExamClaim() {
    ClaimCase claim =
        ClaimCase.draft(
            1001L,
            42L,
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
                    1001L,
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

  private static DecisionNodeHandler failing(String name) {
    return new DecisionNodeHandler() {
      @Override
      public String nodeName() {
        return name;
      }

      @Override
      public DecisionNodeOutcome execute(DecisionNodeContext context) {
        throw new IllegalStateException("依赖不可用");
      }
    };
  }

  private DecisionGraphExecutor executor(List<DecisionNodeHandler> handlers) {
    return new DecisionGraphExecutor(
        decisionExecutionApplicationService, claimCaseApplicationService, tenantPort, handlers);
  }

  private static DecisionGraphExecutor.DecisionVersions versions() {
    return new DecisionGraphExecutor.DecisionVersions("g1", "r1", "m1", "p1", "c1");
  }

  @Test
  @DisplayName("全节点成功 + 闸门通过 → AUTO_APPROVE，轨迹按序落 5 条")
  void happyPath() {
    List<DecisionNodeHandler> handlers =
        List.of(
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
                Map.of("rejectSuggested", false),
                new BigDecimal("0.93")),
            node(
                DecisionGraph.ADJUST,
                DecisionNodeStatus.SUCCEEDED,
                Map.of("amount", new BigDecimal("300.00")),
                null),
            node(DecisionGraph.ARBITRATE, DecisionNodeStatus.SUCCEEDED, Map.of(), null));

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(handlers).execute(1001L, versions());

    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(result.traceCount()).isEqualTo(5);
    verify(decisionExecutionApplicationService).persist(persisted.capture());
    DecisionExecution execution = persisted.getValue();
    assertThat(execution.traces()).hasSize(5);
    assertThat(execution.traces().get(0).getNodeName()).isEqualTo(DecisionGraph.EVIDENCE_LOAD);
    assertThat(execution.traces().get(4).getNodeName()).isEqualTo(DecisionGraph.ARBITRATE);
    assertThat(execution.getActiveFlag()).isNull();
  }

  @Test
  @DisplayName("LLM 节点不可用 → 降级纯规则路径（状态 FALLBACK，置信度取规则路径）")
  void llmUnavailableFallsBackToRulePath() {
    List<DecisionNodeHandler> handlers =
        List.of(
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
            failing(DecisionGraph.AI_ASSESS),
            node(
                DecisionGraph.ADJUST,
                DecisionNodeStatus.SUCCEEDED,
                Map.of("amount", new BigDecimal("300.00")),
                null),
            node(DecisionGraph.ARBITRATE, DecisionNodeStatus.SUCCEEDED, Map.of(), null));

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(handlers).execute(1001L, versions());

    verify(decisionExecutionApplicationService).persist(persisted.capture());
    DecisionExecution execution = persisted.getValue();
    assertThat(execution.statusEnum()).isEqualTo(DecisionExecution.Status.FALLBACK);
    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(execution.getAiConfidence()).isEqualByComparingTo(new BigDecimal("0.90"));
    assertThat(execution.traces().get(2).getActionResult()).isEqualTo("LLM_UNAVAILABLE");
  }

  @Test
  @DisplayName("确定性节点失败 → 中止并强制转人工（fail-safe）")
  void deterministicNodeFailureAborts() {
    List<DecisionNodeHandler> handlers =
        List.of(
            node(
                DecisionGraph.EVIDENCE_LOAD,
                DecisionNodeStatus.SUCCEEDED,
                Map.of("hasEvidence", true, "evidenceParsed", true),
                null),
            failing(DecisionGraph.RULE_CHECK));

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(handlers).execute(1001L, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
    verify(decisionExecutionApplicationService).persist(persisted.capture());
    assertThat(persisted.getValue().statusEnum()).isEqualTo(DecisionExecution.Status.FAILED);
  }

  @Test
  @DisplayName("无处理器（能力缺失）→ 强制转人工，不误判为缺件")
  void capabilityMissing() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(List.of()).execute(1001L, versions());

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
    verify(decisionExecutionApplicationService).persist(persisted.capture());
    assertThat(persisted.getValue().traces()).hasSize(5);
    assertThat(persisted.getValue().traces().get(0).getActionResult()).isEqualTo("NO_HANDLER");
  }

  @Test
  @DisplayName("证据未齐 → NEED_MORE_EVIDENCE（区别于能力缺失）")
  void evidenceMissing() {
    List<DecisionNodeHandler> handlers =
        List.of(
            node(
                DecisionGraph.EVIDENCE_LOAD,
                DecisionNodeStatus.SUCCEEDED,
                Map.of("hasEvidence", false),
                null));

    DecisionGraphExecutor.DecisionExecutionResult result =
        executor(handlers).execute(1001L, versions());
    assertThat(result.decisionResult()).isEqualTo("NEED_MORE_EVIDENCE");
  }
}
