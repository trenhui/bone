package com.bone.procurement.governance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.CapabilityNotFoundException;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import com.bone.procurement.governance.infrastructure.InMemoryCapabilityRegistry;
import com.bone.procurement.governance.infrastructure.InMemoryGovernanceTracePort;
import com.bone.procurement.governance.infrastructure.InMemoryHumanDecisionLedger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 应用服务用例测试：四组用例 + 登录态 fail-closed + 审计留痕。 */
class AiGovernanceApplicationServiceTest {

  private CapabilityRegistry registry;
  private HumanDecisionLedger ledger;
  private GovernanceTracePort tracePort;
  private StubActorPort actorPort;
  private AiGovernanceApplicationService service;

  @BeforeEach
  void setUp() {
    registry = new InMemoryCapabilityRegistry();
    ledger = new InMemoryHumanDecisionLedger();
    tracePort = new InMemoryGovernanceTracePort();
    actorPort = new StubActorPort("admin-1");
    service = new AiGovernanceApplicationService(registry, ledger, tracePort, actorPort);
  }

  @Test
  void 登记后缺省不放行启用后放行() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    assertThat(service.decide("M2").allowed()).isFalse();
    service.changeState("enable", new ChangeCapabilityStateCommand("M2", "评审通过"));
    GovernanceDecision d = service.decide("M2");
    assertThat(d.allowed()).isTrue();
    assertThat(d.aiOnlyAdvisory()).isTrue();
  }

  @Test
  void 未登记能力裁决failClosed() {
    GovernanceDecision d = service.decide("MISSING");
    assertThat(d.allowed()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(GovernanceDecision.REASON_UNKNOWN_CAPABILITY);
  }

  @Test
  void 一键停用演练() {
    service.register(new RegisterCapabilityCommand("M4", "辅助评标", "u-1", "张三"));
    service.changeState("enable", new ChangeCapabilityStateCommand("M4", "启用"));
    assertThat(service.decide("M4").allowed()).isTrue();
    service.changeState("disable", new ChangeCapabilityStateCommand("M4", "停机演练"));
    assertThat(service.decide("M4").allowed()).isFalse();
    assertThat(service.getCapability("M4").state()).isEqualTo("DISABLED");
  }

  @Test
  void 人工终决override回流评测增补() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    HumanDecisionView view =
        service.recordHumanDecision(
            new RecordHumanDecisionCommand(
                "hfd-1", "M2", "CT-1", "LOW", 0.95, "HIGH", "人工判定条款高风险", 5000));
    assertThat(view.override()).isTrue();
    assertThat(view.evaluationFeedbackRaised()).isTrue();
    assertThat(view.humanHasFinalAuthority()).isTrue();
    assertThat(view.reviewer()).isEqualTo("admin-1");
    assertThat(service.countOverrides("M2")).isEqualTo(1);
  }

  @Test
  void 人工终决复核人取登录态而非命令自报() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    // 命令里根本没有 reviewer 字段，复核人只能来自 actorPort。
    HumanDecisionView view =
        service.recordHumanDecision(
            new RecordHumanDecisionCommand(null, "M2", "CT-2", "HIGH", 0.9, "HIGH", "", 3000));
    assertThat(view.reviewer()).isEqualTo("admin-1");
  }

  @Test
  void 无登录态时治理操作与人工终决failClosed() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    actorPort.actorId = null;
    assertThatThrownBy(
            () -> service.changeState("disable", new ChangeCapabilityStateCommand("M2", "x")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("登录态");
    assertThatThrownBy(
            () ->
                service.recordHumanDecision(
                    new RecordHumanDecisionCommand(null, "M2", "CT-3", "L", 0.5, "H", "", 1)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 未登记能力变更抛异常() {
    assertThatThrownBy(() -> service.getCapability("MISSING"))
        .isInstanceOf(CapabilityNotFoundException.class);
  }

  @Test
  void 重复登记被拒绝() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    assertThatThrownBy(
            () -> service.register(new RegisterCapabilityCommand("M2", "重复", "u-1", "张三")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 治理裁决与终决全程留痕() {
    service.register(new RegisterCapabilityCommand("M2", "合同审查", "u-1", "张三"));
    service.decide("M2");
    service.changeState("disable", new ChangeCapabilityStateCommand("M2", "停机"));
    service.recordHumanDecision(
        new RecordHumanDecisionCommand("hfd-9", "M2", "CT-9", "LOW", 0.8, "HIGH", "", 2000));
    List<GovernanceTraceEntry> entries = service.trace("M2");
    assertThat(entries)
        .extracting(GovernanceTraceEntry::eventType)
        .contains("REGISTERED", "DECISION", "DISABLED", "HUMAN_OVERRIDE");
  }

  private static final class StubActorPort implements GovernanceActorPort {
    private String actorId;

    StubActorPort(String actorId) {
      this.actorId = actorId;
    }

    @Override
    public String currentActorId() {
      return actorId;
    }
  }
}
