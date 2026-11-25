package com.bone.procurement.governance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityNotFoundException;
import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;
import com.bone.procurement.governance.domain.model.capability.OversightVerdict;
import com.bone.procurement.governance.domain.service.OversightThresholds;
import com.bone.procurement.governance.infrastructure.InMemoryCapabilityRegistry;
import com.bone.procurement.governance.infrastructure.InMemoryGovernanceTracePort;
import com.bone.procurement.governance.infrastructure.InMemoryHumanDecisionLedger;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 监督有效性只读应用服务测试：真实计数、三类风险、fail-closed、无副作用。 */
class OversightMetricsApplicationTest {

  private CapabilityRegistry registry;
  private HumanDecisionLedger ledger;
  private GovernanceTracePort tracePort;
  private AiGovernanceApplicationService service;

  @BeforeEach
  void setUp() {
    registry = new InMemoryCapabilityRegistry();
    ledger = new InMemoryHumanDecisionLedger();
    tracePort = new InMemoryGovernanceTracePort();
    service =
        new AiGovernanceApplicationService(
            registry, ledger, tracePort, (GovernanceActorPort) () -> "admin-1");
  }

  private void register() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
  }

  private void decide(
      String id, String ai, String finalConclusion, String reason, long durationMs) {
    service.recordHumanDecision(
        new RecordHumanDecisionCommand(
            id, "M2", "CT-" + id, ai, 0.9, finalConclusion, reason, durationMs));
  }

  /** 历史遗留：override 但未回流评测（evaluationFeedback=false），此类数据只能通过台账直接落。 */
  private void appendLegacyNoFeedback(String id) {
    ledger.append(
        new HumanFinalDecision(
            id,
            new CapabilityCode("M2"),
            "CT-" + id,
            "LOW",
            0.9,
            "HIGH",
            "admin-1",
            "历史推翻",
            2000,
            true,
            false,
            Instant.now()));
  }

  @Test
  void 真实计数一致且无理由推翻优先触阈() {
    register();
    for (int i = 0; i < 3; i++) decide("f-" + i, "HIGH", "HIGH", "", 50); // 橡皮图章
    for (int i = 0; i < 3; i++) decide("u-" + i, "LOW", "HIGH", "", 2000); // 无理由推翻
    for (int i = 0; i < 3; i++) appendLegacyNoFeedback("n-" + i); // 未回流评测
    for (int i = 0; i < 1; i++) decide("g-" + i, "HIGH", "HIGH", "", 3000); // 有效复核

    OversightMetricsView view =
        service.oversightSignal("M2", new OversightThresholds(1000L, 3, 0.3));
    assertThat(view.sampleSize()).isEqualTo(10);
    assertThat(view.fastReviewCount()).isEqualTo(3);
    assertThat(view.unreasonedOverrideCount()).isEqualTo(3);
    assertThat(view.feedbackNotRaisedCount()).isEqualTo(3);
    assertThat(view.verdict()).isEqualTo(OversightVerdict.REVIEW_REQUIRED.name());
    assertThat(view.reasonCode()).isEqualTo("OVERSIGHT_UNREASONED_OVERRIDE");
    assertThat(view.requiresOwnerReview()).isTrue();
    assertThat(view.modelHealthClaim()).isEqualTo("NONE");
  }

  @Test
  void 仅橡皮图章触阈() {
    register();
    for (int i = 0; i < 3; i++) decide("f-" + i, "HIGH", "HIGH", "", 50);
    for (int i = 0; i < 7; i++) decide("g-" + i, "HIGH", "HIGH", "", 3000);
    OversightMetricsView view =
        service.oversightSignal("M2", new OversightThresholds(1000L, 3, 0.3));
    assertThat(view.verdict()).isEqualTo(OversightVerdict.REVIEW_REQUIRED.name());
    assertThat(view.reasonCode()).isEqualTo("OVERSIGHT_FAST_REVIEW_RATIO");
  }

  @Test
  void 阈值缺省failClosed且不输出误导性计数() {
    register();
    decide("g-0", "HIGH", "HIGH", "", 50);
    OversightMetricsView view = service.oversightSignal("M2", OversightThresholds.unconfigured());
    assertThat(view.verdict()).isEqualTo(OversightVerdict.UNDETERMINED.name());
    assertThat(view.reasonCode()).isEqualTo("OVERSIGHT_THRESHOLD_NOT_CONFIGURED");
    assertThat(view.fastReviewCount())
        .as("阈值未配置时不得把全部样本算作橡皮图章，避免 UNDETERMINED 判词下外泄误导性质量结论")
        .isZero();
  }

  @Test
  void 样本不足failClosed() {
    register();
    decide("f-0", "HIGH", "HIGH", "", 50);
    OversightMetricsView view =
        service.oversightSignal("M2", new OversightThresholds(1000L, 3, 0.3));
    assertThat(view.verdict()).isEqualTo(OversightVerdict.UNDETERMINED.name());
    assertThat(view.reasonCode()).isEqualTo("OVERSIGHT_INSUFFICIENT_SAMPLE");
  }

  @Test
  void 无风险信号不构成背书() {
    register();
    for (int i = 0; i < 10; i++) decide("g-" + i, "HIGH", "HIGH", "", 3000);
    OversightMetricsView view =
        service.oversightSignal("M2", new OversightThresholds(1000L, 3, 0.3));
    assertThat(view.verdict()).isEqualTo(OversightVerdict.NO_ACTIONABLE_SIGNAL.name());
    assertThat(view.modelHealthClaim()).isEqualTo("NONE");
  }

  @Test
  void API新建override不命中未回流评测诚实边界() {
    register();
    // 既有人工终决写入路径：override 自动置 evaluationFeedbackRaised=true
    decide("new-0", "LOW", "HIGH", "推翻理由", 2000);
    OversightMetricsView view =
        service.oversightSignal("M2", new OversightThresholds(1000L, 3, 0.3));
    assertThat(view.unreasonedOverrideCount()).isZero();
    assertThat(view.feedbackNotRaisedCount()).as("既有写入路径下新 override 自动回流，不计入未回流评测").isZero();
  }

  @Test
  void 未登记能力被拒绝() {
    assertThatThrownBy(() -> service.oversightSignal("M2", OversightThresholds.unconfigured()))
        .isInstanceOf(CapabilityNotFoundException.class);
  }

  @Test
  void 读操作无副作用() {
    register();
    decide("u-0", "LOW", "HIGH", "推翻", 2000);
    int traceBefore = tracePort.listByCapability("M2").size();
    int ledgerBefore = ledger.findByCapability(new CapabilityCode("M2")).size();
    for (int i = 0; i < 3; i++) {
      service.oversightSignal("M2", new OversightThresholds(1000L, 3, 0.3));
    }
    assertThat(tracePort.listByCapability("M2")).hasSize(traceBefore);
    assertThat(ledger.findByCapability(new CapabilityCode("M2"))).hasSize(ledgerBefore);
  }

  @Test
  void 投影不回传理由原文() {
    register();
    decide("p-0", "HIGH", "HIGH", "SENSITIVE_REASON_TEXT", 3000);
    OversightMetricsView view =
        service.oversightSignal("M2", new OversightThresholds(1000L, 3, 0.3));
    assertThat(view.toString()).doesNotContain("SENSITIVE_REASON_TEXT");
  }
}
