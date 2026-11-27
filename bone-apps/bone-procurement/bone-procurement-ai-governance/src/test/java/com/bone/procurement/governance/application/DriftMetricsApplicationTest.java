package com.bone.procurement.governance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.CapabilityNotFoundException;
import com.bone.procurement.governance.domain.model.capability.DriftSignal;
import com.bone.procurement.governance.domain.model.capability.DriftThresholds;
import com.bone.procurement.governance.domain.model.capability.DriftVerdict;
import com.bone.procurement.governance.infrastructure.InMemoryCapabilityRegistry;
import com.bone.procurement.governance.infrastructure.InMemoryGovernanceTracePort;
import com.bone.procurement.governance.infrastructure.InMemoryHumanDecisionLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 治理指标只读应用服务测试：真实计数、fail-closed、只读无副作用。 */
class DriftMetricsApplicationTest {

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

  private void registerAndDecide(int overrides, int same) {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    for (int i = 0; i < overrides; i++) {
      service.recordHumanDecision(
          new RecordHumanDecisionCommand(
              "o-" + i, "M2", "CT-o-" + i, "LOW", 0.9, "HIGH", "推翻", 1000));
    }
    for (int i = 0; i < same; i++) {
      service.recordHumanDecision(
          new RecordHumanDecisionCommand(
              "s-" + i, "M2", "CT-s-" + i, "HIGH", 0.9, "HIGH", "", 500));
    }
  }

  @Test
  void 真实计数一致性与触阈需复核() {
    registerAndDecide(3, 7);
    DriftSignal signal = service.driftSignal("M2", new DriftThresholds(0.25, 5));
    assertThat(signal.sampleSize()).isEqualTo(10);
    assertThat(signal.overrideCount()).isEqualTo(3);
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.REVIEW_REQUIRED);
    assertThat(signal.overrideRate()).isBetween(0.29, 0.31);
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 阈值缺省时failClosed不可判定() {
    registerAndDecide(3, 7);
    DriftSignal signal = service.driftSignal("M2", DriftThresholds.unconfigured());
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.UNDETERMINED);
    assertThat(signal.reasonCode()).isEqualTo(DriftSignal.REASON_THRESHOLD_NOT_CONFIGURED);
  }

  @Test
  void 样本不足时failClosed不可判定() {
    registerAndDecide(1, 1);
    DriftSignal signal = service.driftSignal("M2", new DriftThresholds(0.3, 5));
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.UNDETERMINED);
    assertThat(signal.reasonCode()).isEqualTo(DriftSignal.REASON_INSUFFICIENT_SAMPLE);
  }

  @Test
  void 未触阈不构成模型背书() {
    registerAndDecide(1, 9);
    DriftSignal signal = service.driftSignal("M2", new DriftThresholds(0.3, 5));
    assertThat(signal.verdict()).isEqualTo(DriftVerdict.NO_ACTIONABLE_SIGNAL);
    assertThat(signal.claimsModelHealthy()).isFalse();
  }

  @Test
  void 读操作无副作用() {
    registerAndDecide(3, 7);
    int traceBefore = service.trace("M2").size();
    service.driftSignal("M2", new DriftThresholds(0.25, 5));
    service.driftSignal("M2", new DriftThresholds(0.25, 5));
    assertThat(service.trace("M2")).hasSize(traceBefore);
    assertThat(service.countOverrides("M2")).isEqualTo(3);
  }

  @Test
  void 未登记能力不可评估() {
    assertThatThrownBy(() -> service.driftSignal("MISSING", DriftThresholds.unconfigured()))
        .isInstanceOf(CapabilityNotFoundException.class);
  }
}
