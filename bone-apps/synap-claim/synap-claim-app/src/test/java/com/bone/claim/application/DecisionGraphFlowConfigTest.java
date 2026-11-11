package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.DecisionFlowConfigPort;
import com.bone.claim.application.port.out.DecisionMetricsPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.model.decision.DecisionNodeStatus;
import com.bone.claim.domain.model.decision.TriageLimits;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * M5「配了不生效」反证测试：删掉执行器里的流程配置读取点，{@link #configuredSkipForcesManualReview()}
 * 必须转红；把「跳过即转人工」改成「跳过直接放行」，同一用例也必须转红。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionGraphFlowConfigTest {

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
            "idem-key-test-0",
            "v1",
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
    claim.accept();
    claim.allocateToPreExam("op-1");
    return claim;
  }

  private static DecisionNodeHandler node(
      String name, Map<String, Object> output, BigDecimal confidence) {
    return new DecisionNodeHandler() {
      @Override
      public String nodeName() {
        return name;
      }

      @Override
      public DecisionNodeOutcome execute(DecisionNodeContext context) {
        return new DecisionNodeOutcome(
            DecisionNodeStatus.SUCCEEDED, output, confidence, "摘要", "OK");
      }
    };
  }

  private static List<DecisionNodeHandler> happyHandlers() {
    return List.of(
        node(
            DecisionGraph.EVIDENCE_LOAD,
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
        node(DecisionGraph.AI_ASSESS, Map.of("rejectSuggested", false), new BigDecimal("0.93")),
        node(DecisionGraph.ADJUST, Map.of("amount", new BigDecimal("300.00")), null),
        node(DecisionGraph.ARBITRATE, Map.of(), null));
  }

  private DecisionGraphExecutor.DecisionExecutionResult run(DecisionFlowConfigPort port) {
    DecisionGraphExecutor executor =
        new DecisionGraphExecutor(
            decisionExecutionApplicationService,
            claimCaseApplicationService,
            tenantPort,
            DecisionMetricsPort.NOOP,
            tenantId -> TriageLimits.defaultResolution(),
            happyHandlers(),
            null,
            port);
    return executor.execute(
        1001L, new DecisionGraphExecutor.DecisionVersions("g1", "r1", "m1", "p1", "c1"));
  }

  @Test
  @DisplayName("空集 / 无端口：与改造前逐位一致，仍 AUTO_APPROVE 且 5 条轨迹")
  void noConfigKeepsCanonicalBehaviour() {
    assertThat(run(tenantId -> Set.of()).decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(run(null).traceCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("配置 skipNodes=[AiAssess]：结论真的改判 MANUAL_REQUIRED 并留原因码")
  void configuredSkipForcesManualReview() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        run(tenantId -> Set.of(DecisionGraph.AI_ASSESS));

    assertThat(result.decisionResult()).isEqualTo("MANUAL_REQUIRED");
    assertThat(result.traceCount()).isEqualTo(5);
    verify(decisionExecutionApplicationService).persist(persisted.capture());
    assertThat(persisted.getValue().getTriageReason()).isEqualTo("FLOW_CONFIG_AI_SKIPPED");
  }

  @Test
  @DisplayName("端口返回白名单外的 Adjust：执行侧二次确认必须拦住，金额节点照常执行")
  void nonSkippableNodeIsNeverSkipped() {
    DecisionGraphExecutor.DecisionExecutionResult result =
        run(tenantId -> Set.of(DecisionGraph.ADJUST));

    assertThat(result.decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(result.traceCount()).isEqualTo(5);
    verify(decisionExecutionApplicationService).persist(persisted.capture());
    assertThat(persisted.getValue().getTriageReason()).isEqualTo("ALL_GATES_PASSED");
  }

  @Test
  @DisplayName("端口抛异常：fail-closed 回落到按原图跑，不放宽结论")
  void portFailureFallsBackToCanonical() {
    DecisionFlowConfigPort broken =
        tenantId -> {
          throw new IllegalStateException("配置中心不可用");
        };
    assertThat(run(broken).decisionResult()).isEqualTo("AUTO_APPROVE");
  }
}
