package com.bone.procurement.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.DriftThresholds;
import com.bone.procurement.governance.domain.model.capability.OversightVerdict;
import com.bone.procurement.governance.domain.model.capability.PostureVerdict;
import com.bone.procurement.governance.domain.service.OversightThresholds;
import com.bone.procurement.governance.infrastructure.InMemoryCapabilityRegistry;
import com.bone.procurement.governance.infrastructure.InMemoryGovernanceTracePort;
import com.bone.procurement.governance.infrastructure.InMemoryHumanDecisionLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 治理态势应用聚合：确定性汇总、只读无副作用、阈值缺省 fail-closed。 */
class GovernancePostureApplicationTest {

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

  private long traceCount() {
    return tracePort.listByCapability("M2").size();
  }

  @Test
  void 阈值未配置时汇总不可判定且只读无副作用() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    long before = traceCount();
    GovernancePostureView view =
        service.posture(DriftThresholds.unconfigured(), OversightThresholds.unconfigured());
    assertThat(view.verdict()).isEqualTo(PostureVerdict.UNDETERMINED.name());
    assertThat(view.capabilities()).hasSize(1);
    assertThat(view.undeterminedCount()).isEqualTo(1);
    assertThat(traceCount()).isEqualTo(before);
  }

  @Test
  void 触阈时汇总需关注且明细给出监督判词() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    for (int i = 0; i < 3; i++) {
      service.recordHumanDecision(
          new RecordHumanDecisionCommand(
              "o-" + i, "M2", "CT-o-" + i, "LOW", 0.9, "HIGH", "推翻", 100));
    }
    GovernancePostureView view =
        service.posture(new DriftThresholds(0.3, 3), new OversightThresholds(1000L, 3, 0.5));
    assertThat(view.verdict()).isEqualTo(PostureVerdict.ATTENTION_REQUIRED.name());
    assertThat(view.attentionCount()).isGreaterThanOrEqualTo(1);
    assertThat(view.capabilities().get(0).oversightVerdict())
        .isEqualTo(OversightVerdict.REVIEW_REQUIRED.name());
  }

  @Test
  void 全部可判定且无信号时汇总全清() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    service.recordHumanDecision(
        new RecordHumanDecisionCommand("s-1", "M2", "CT-s-1", "HIGH", 0.9, "HIGH", "", 2000));
    GovernancePostureView view =
        service.posture(new DriftThresholds(0.3, 1), new OversightThresholds(1000L, 1, 0.5));
    assertThat(view.verdict()).isEqualTo(PostureVerdict.ALL_CLEAR.name());
    assertThat(view.attentionCount()).isZero();
    assertThat(view.undeterminedCount()).isZero();
  }
}
