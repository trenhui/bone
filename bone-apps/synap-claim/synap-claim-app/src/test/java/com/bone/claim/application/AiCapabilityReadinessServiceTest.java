package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AiCapabilityReadinessService.ReadinessSnapshot;
import com.bone.claim.application.port.out.AiCapabilityDeclaration;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.infrastructure.ai.LocalAiGatewayPortAdapter;
import com.bone.claim.infrastructure.forensics.LocalVisualForensicsPortAdapter;
import com.bone.claim.infrastructure.fraud.LocalGraphFraudPortAdapter;
import com.bone.claim.infrastructure.knowledge.LocalKnowledgeRetrievalPortAdapter;
import com.bone.claim.infrastructure.rule.LocalRuleEnginePortAdapter;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AI 能力清单（监管 §15）的契约测试。
 *
 * <p>锁定的不变量：
 *
 * <ol>
 *   <li><b>清单不可自我美化</b>：声明缺失（null）、声明抛异常、适配器未覆盖 {@code declaration()} 三种情况一律判 UNKNOWN
 *       并计入阻断项，绝不因「拿不到状态」就假设能力已就绪；
 *   <li><b>部署态 ≠ 运行期 ≠ 放款许可</b>：即使全部声明 READY 且开关开启，响应仍须随带口径声明，明确逐案运行期证据 与监管批准不在清单能力范围内；
 *   <li><b>声明与实现不得漂移</b>：一期本地桩的声明必须非 READY，且其运行期 {@code available()} 必须为 false （{@code
 *       LocalAiGatewayPortAdapter} 是刻意的例外：它自报 available=true 但仍是桩，正好用来证明 「运行期 available」不能替代部署态声明）。
 * </ol>
 */
class AiCapabilityReadinessServiceTest {

  private final RuleEnginePort rule = mock(RuleEnginePort.class);
  private final VisualForensicsPort visual = mock(VisualForensicsPort.class);
  private final GraphFraudPort graph = mock(GraphFraudPort.class);
  private final KnowledgeRetrievalPort knowledge = mock(KnowledgeRetrievalPort.class);
  private final AiGatewayPort ai = mock(AiGatewayPort.class);

  private AiCapabilityReadinessService service(boolean autoApproveEnabled) {
    AutoApprovalApplicationService autoApproval = mock(AutoApprovalApplicationService.class);
    when(autoApproval.isAutoApproveEnabled()).thenReturn(autoApproveEnabled);
    return new AiCapabilityReadinessService(rule, visual, graph, knowledge, ai, autoApproval);
  }

  @Test
  @DisplayName("默认部署态：开关关闭 + 能力未接线 → 闸门不通过，阻断项完整且均为受控码")
  void defaultDeploymentIsBlocked() {
    when(rule.declaration())
        .thenReturn(
            new AiCapabilityDeclaration(
                "C2_RULE", AiCapabilityDeclaration.STATE_READY, "CONSTANT_RULESET_ONLY", "test"));
    when(visual.declaration())
        .thenReturn(AiCapabilityDeclaration.stub("E1_VISUAL", "ANTI_FRAUD_E1_NOT_INTEGRATED", "x"));
    when(graph.declaration())
        .thenReturn(AiCapabilityDeclaration.stub("E2_GRAPH", "ANTI_FRAUD_E2_NOT_INTEGRATED", "x"));
    when(knowledge.declaration())
        .thenReturn(AiCapabilityDeclaration.stub("E3_KNOWLEDGE", "KNOWLEDGE_UNAVAILABLE", "x"));
    when(ai.declaration())
        .thenReturn(
            AiCapabilityDeclaration.notConfigured("C5_LLM", "AI_GATEWAY_BASE_URL_MISSING", "x"));

    ReadinessSnapshot snapshot = service(false).snapshot();

    assertThat(snapshot.riskTier()).isEqualTo("HIGH");
    assertThat(snapshot.autoApproveEnabled()).isFalse();
    assertThat(snapshot.capabilities()).hasSize(5);
    assertThat(snapshot.deploymentGatesPassed()).isFalse();
    assertThat(snapshot.blockers())
        .contains(
            "AUTO_APPROVE_TOGGLE_OFF",
            "E1_VISUAL:LOCAL_STUB:ANTI_FRAUD_E1_NOT_INTEGRATED",
            "E2_GRAPH:LOCAL_STUB:ANTI_FRAUD_E2_NOT_INTEGRATED",
            "E3_KNOWLEDGE:LOCAL_STUB:KNOWLEDGE_UNAVAILABLE",
            "C5_LLM:NOT_CONFIGURED:AI_GATEWAY_BASE_URL_MISSING")
        .doesNotContain("C2_RULE:LOCAL_STUB:RULE_ENGINE_STUB");
    // READY 的能力仍必须出现在清单里（清单是「全部能力」，不是「仅异常能力」）
    assertThat(snapshot.capabilities())
        .anySatisfy(
            c -> {
              assertThat(c.capabilityId()).isEqualTo("C2_RULE");
              assertThat(c.reason()).isEqualTo("CONSTANT_RULESET_ONLY");
            });
  }

  @Test
  @DisplayName("负向探针：适配器未自报声明（null）→ 一律 UNKNOWN，绝不假设能力已就绪")
  void missingDeclarationFallsBackToUnknown() {
    // 故意不 stub 任何 declaration()：Mockito 对 default 方法返回 null
    ReadinessSnapshot snapshot = service(true).snapshot();

    assertThat(snapshot.capabilities()).hasSize(5);
    assertThat(snapshot.capabilities())
        .allSatisfy(
            c -> {
              assertThat(c.state()).isEqualTo(AiCapabilityDeclaration.STATE_UNKNOWN);
              assertThat(c.reason()).isEqualTo("DECLARATION_MISSING");
              assertThat(c.real()).isFalse();
            });
    assertThat(snapshot.deploymentGatesPassed()).isFalse();
    assertThat(snapshot.blockers())
        .hasSize(5)
        .allSatisfy(b -> assertThat(b).contains(":UNKNOWN:DECLARATION_MISSING"));
  }

  @Test
  @DisplayName("负向探针：声明求值抛异常 → 回落 UNKNOWN 而不是中断清单")
  void throwingDeclarationFallsBackToUnknown() {
    when(rule.declaration()).thenThrow(new IllegalStateException("boom"));
    when(visual.declaration()).thenReturn(AiCapabilityDeclaration.ready("E1_VISUAL", "test"));
    when(graph.declaration()).thenReturn(AiCapabilityDeclaration.ready("E2_GRAPH", "test"));
    when(knowledge.declaration()).thenReturn(AiCapabilityDeclaration.ready("E3_KNOWLEDGE", "test"));
    when(ai.declaration()).thenReturn(AiCapabilityDeclaration.ready("C5_LLM", "test"));

    ReadinessSnapshot snapshot = service(true).snapshot();

    assertThat(snapshot.capabilities().get(0).state())
        .isEqualTo(AiCapabilityDeclaration.STATE_UNKNOWN);
    assertThat(snapshot.deploymentGatesPassed()).isFalse();
    assertThat(snapshot.blockers()).contains("C2_RULE:UNKNOWN:DECLARATION_MISSING");
  }

  @Test
  @DisplayName("负向探针：未覆盖 declaration() 的新适配器 → 端口 default 必须回落 UNKNOWN")
  void uncoveredPortImplementationFallsBackToUnknown() {
    VisualForensicsPort uncovered =
        ctx -> new VisualForensicsPort.ForensicsResult(false, BigDecimal.ZERO, false);
    AiCapabilityDeclaration declaration = uncovered.declaration();

    assertThat(declaration.state()).isEqualTo(AiCapabilityDeclaration.STATE_UNKNOWN);
    assertThat(declaration.real()).isFalse();
  }

  @Test
  @DisplayName("部署态闸门全通过也不等于放款许可：响应必须自带口径声明")
  void gatesPassedIsNotPaymentPermission() {
    when(rule.declaration()).thenReturn(AiCapabilityDeclaration.ready("C2_RULE", "test"));
    when(visual.declaration()).thenReturn(AiCapabilityDeclaration.ready("E1_VISUAL", "test"));
    when(graph.declaration()).thenReturn(AiCapabilityDeclaration.ready("E2_GRAPH", "test"));
    when(knowledge.declaration()).thenReturn(AiCapabilityDeclaration.ready("E3_KNOWLEDGE", "test"));
    when(ai.declaration()).thenReturn(AiCapabilityDeclaration.ready("C5_LLM", "test"));

    ReadinessSnapshot snapshot = service(true).snapshot();

    assertThat(snapshot.deploymentGatesPassed()).isTrue();
    assertThat(snapshot.blockers()).isEmpty();
    assertThat(snapshot.scopeNote()).contains("不构成放款许可", "运行期", "风险管理委员会批准", "人工干预");
    assertThat(snapshot.autoApproveEnabled()).isTrue();
  }

  @Test
  @DisplayName("声明与实现一致性：一期本地桩不得自报 READY，且运行期 available=false")
  void stubDeclarationsMatchRuntimeAvailability() {
    ClaimContextPort.ClaimContextView ctx =
        new ClaimContextPort.ClaimContextView(
            1001L,
            "OUTPATIENT",
            new BigDecimal("800.00"),
            true,
            true,
            new BigDecimal("0.95"),
            true);

    LocalRuleEnginePortAdapter ruleStub = new LocalRuleEnginePortAdapter();
    LocalVisualForensicsPortAdapter visualStub = new LocalVisualForensicsPortAdapter();
    LocalGraphFraudPortAdapter graphStub = new LocalGraphFraudPortAdapter();
    LocalKnowledgeRetrievalPortAdapter knowledgeStub = new LocalKnowledgeRetrievalPortAdapter();
    LocalAiGatewayPortAdapter aiStub = new LocalAiGatewayPortAdapter();

    assertThat(ruleStub.declaration().real()).isFalse();
    assertThat(visualStub.declaration().real()).isFalse();
    assertThat(graphStub.declaration().real()).isFalse();
    assertThat(knowledgeStub.declaration().real()).isFalse();
    assertThat(aiStub.declaration().real()).isFalse();

    assertThat(ruleStub.check(ctx).available()).isFalse();
    assertThat(visualStub.inspect(ctx).available()).isFalse();
    assertThat(graphStub.screen(ctx).available()).isFalse();
    assertThat(knowledgeStub.match(ctx).available()).isFalse();

    // 刻意的例外：本地网关桩为演示/回归基线自报 available=true，但部署态仍是 STUB。
    // 这条断言证明「运行期 available」不能替代部署态声明——否则清单会把演示桩报成真实 LLM。
    assertThat(aiStub.assess(ctx).available()).isTrue();
    assertThat(aiStub.declaration().state()).isEqualTo(AiCapabilityDeclaration.STATE_LOCAL_STUB);
  }

  @Test
  @DisplayName("归一化：null 字段不落 null，缺失 state 回落 UNKNOWN")
  void nullFieldsAreNormalized() {
    AiCapabilityDeclaration d = new AiCapabilityDeclaration(null, null, null, null);

    assertThat(d.capabilityId()).isEmpty();
    assertThat(d.state()).isEqualTo(AiCapabilityDeclaration.STATE_UNKNOWN);
    assertThat(d.reason()).isEmpty();
    assertThat(d.remediation()).isEmpty();
    assertThat(d.real()).isFalse();
  }

  // 各用例显式构造独立桩，避免 Mockito 嵌套桩导致 UnfinishedStubbing（本模块既有纪律）。
  @Test
  @DisplayName("清单不可变：blockers 与 capabilities 均为不可变快照")
  void snapshotIsImmutable() {
    ReadinessSnapshot snapshot = service(false).snapshot();

    assertThatThrownBy(() -> snapshot.capabilities().add(AiCapabilityDeclaration.ready("X", "x")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.blockers().add("X"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
