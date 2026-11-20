package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AiCapabilityReadinessService.ReadinessSnapshot;
import com.bone.claim.application.EngineeringEvidenceService.EvidenceCriterion;
import com.bone.claim.application.EngineeringEvidenceService.EvidenceView;
import com.bone.claim.application.port.out.AiCapabilityDeclaration;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 工程证据底座契约测试（防假绿）。
 *
 * <p>锁定的不变量：
 *
 * <ol>
 *   <li>证据视图只读：每项判据含稳定 ID / 受控状态 / 原因码，不含自由文本；
 *   <li>证据视图不替代门禁：即便声明全 READY、开关开启，证据视图也不会把 L3 就绪度翻绿（门禁仍由复核器独立判定）；
 *   <li>声明缺失一律 fail-closed：拿不到声明的维度按 NOT_EVIDENCED 记，绝不假设已就绪。
 * </ol>
 */
class EngineeringEvidenceServiceTest {

  private final RuleEnginePort rule = mock(RuleEnginePort.class);
  private final VisualForensicsPort visual = mock(VisualForensicsPort.class);
  private final GraphFraudPort graph = mock(GraphFraudPort.class);
  private final KnowledgeRetrievalPort knowledge = mock(KnowledgeRetrievalPort.class);
  private final AiGatewayPort ai = mock(AiGatewayPort.class);

  private AiCapabilityReadinessService readiness(boolean autoApproveEnabled) {
    AutoApprovalApplicationService autoApproval = mock(AutoApprovalApplicationService.class);
    when(autoApproval.isAutoApproveEnabled()).thenReturn(autoApproveEnabled);
    return new AiCapabilityReadinessService(rule, visual, graph, knowledge, ai, autoApproval);
  }

  private void stubAllDeclarations() {
    when(rule.declaration())
        .thenReturn(AiCapabilityDeclaration.stub("C2_RULE", "RULE_ENGINE_LOCAL_STUB", "x"));
    when(visual.declaration())
        .thenReturn(AiCapabilityDeclaration.stub("E1_VISUAL", "ANTI_FRAUD_E1_NOT_INTEGRATED", "x"));
    when(graph.declaration())
        .thenReturn(AiCapabilityDeclaration.stub("E2_GRAPH", "ANTI_FRAUD_E2_NOT_INTEGRATED", "x"));
    when(knowledge.declaration())
        .thenReturn(AiCapabilityDeclaration.stub("E3_KNOWLEDGE", "KNOWLEDGE_UNAVAILABLE", "x"));
    when(ai.declaration())
        .thenReturn(
            AiCapabilityDeclaration.notConfigured("C5_LLM", "AI_GATEWAY_BASE_URL_MISSING", "x"));
  }

  @Test
  @DisplayName("默认部署态：证据视图含全部稳定判据 ID，未接线维度判 NOT_EVIDENCED，无一项 EVIDENCED")
  void defaultViewIsFailClosed() {
    stubAllDeclarations();
    EngineeringEvidenceService service = new EngineeringEvidenceService(readiness(false));

    EvidenceView view = service.view();

    assertThat(view.criteria()).isNotEmpty();
    assertThat(view.criteria())
        .extracting(EvidenceCriterion::id)
        .contains(
            "AUTO_APPROVE_TOGGLE",
            "C2_RULE_DECLARATION",
            "E1_VISUAL_DECLARATION",
            "E2_GRAPH_DECLARATION",
            "E3_KNOWLEDGE_DECLARATION",
            "C5_LLM_PROVIDER_CONFIG",
            "LIMITED_DIMENSION_REACHABILITY",
            "FAIL_CLOSED_TRACE_REASON");
    assertThat(view.criteria())
        .allSatisfy(c -> assertThat(c.status()).isIn("EVIDENCED", "NOT_EVIDENCED"));
    assertThat(
            view.criteria().stream()
                .filter(c -> !"FAIL_CLOSED_TRACE_REASON".equals(c.id()))
                .anyMatch(c -> "EVIDENCED".equals(c.status())))
        .isFalse();
    assertThat(
            view.criteria().stream()
                .filter(c -> "FAIL_CLOSED_TRACE_REASON".equals(c.id()))
                .findFirst()
                .orElseThrow()
                .reason())
        .isEqualTo("TRACE_REASON_WIRED");
    assertThat(view.note()).contains("不构成放款许可", "不替代");
  }

  @Test
  @DisplayName("证据视图不替代门禁：即便全部声明 READY 且开关开启，deploymentGatesPassed 也不被证据视图改写")
  void evidenceDoesNotFlipGate() {
    when(rule.declaration()).thenReturn(AiCapabilityDeclaration.ready("C2_RULE", "x"));
    when(visual.declaration()).thenReturn(AiCapabilityDeclaration.ready("E1_VISUAL", "x"));
    when(graph.declaration()).thenReturn(AiCapabilityDeclaration.ready("E2_GRAPH", "x"));
    when(knowledge.declaration()).thenReturn(AiCapabilityDeclaration.ready("E3_KNOWLEDGE", "x"));
    when(ai.declaration()).thenReturn(AiCapabilityDeclaration.ready("C5_LLM", "x"));
    AiCapabilityReadinessService ready = readiness(true);
    EngineeringEvidenceService service = new EngineeringEvidenceService(ready);

    EvidenceView view = service.view();

    ReadinessSnapshot snapshot = ready.snapshot();
    assertThat(snapshot.deploymentGatesPassed()).isTrue();
    // 证据视图自身不含任何「门禁已通过/可放款」字段——它只描述判据，永不给放行结论。
    assertThat(view.criteria())
        .extracting(EvidenceCriterion::id)
        .doesNotContain("L3_CLAIMABLE", "AUTO_APPROVE_ALLOWED");
  }

  @Test
  @DisplayName("声明缺失（null）：拿不到声明的维度判 NOT_EVIDENCED，原因码为 DECLARATION_MISSING，禁止静默假设就绪")
  void missingDeclarationIsNotEvidenced() {
    when(rule.declaration()).thenReturn(null);
    when(visual.declaration()).thenReturn(null);
    when(graph.declaration()).thenReturn(null);
    when(knowledge.declaration()).thenReturn(null);
    when(ai.declaration()).thenReturn(null);
    EngineeringEvidenceService service = new EngineeringEvidenceService(readiness(false));

    EvidenceView view = service.view();

    EvidenceCriterion c2 =
        view.criteria().stream()
            .filter(c -> "C2_RULE_DECLARATION".equals(c.id()))
            .findFirst()
            .orElseThrow();
    assertThat(c2.status()).isEqualTo("NOT_EVIDENCED");
    assertThat(c2.reason()).isEqualTo("DECLARATION_MISSING");
  }

  @Test
  @DisplayName("不可达维度判据恒为 NOT_EVIDENCED：当前无配置列即表达不出来，不伪造可达证据")
  void unreachableDimensionNeverEvidenced() {
    stubAllDeclarations();
    EngineeringEvidenceService service = new EngineeringEvidenceService(readiness(true));

    EvidenceCriterion dim =
        service.view().criteria().stream()
            .filter(c -> "LIMITED_DIMENSION_REACHABILITY".equals(c.id()))
            .findFirst()
            .orElseThrow();

    assertThat(dim.status()).isEqualTo("NOT_EVIDENCED");
    assertThat(dim.reason()).isEqualTo("NO_CONFIG_COLUMN");
  }
}
