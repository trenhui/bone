package com.bone.procurement.governance.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.domain.model.capability.AiCapability;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityState;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import com.bone.procurement.governance.domain.model.capability.OwnerRef;
import org.junit.jupiter.api.Test;

/** 准入裁决 fail-closed 单测：五种状态逐一裁决，确保不可判定态不被压成可用。 */
class CapabilityGovernancePolicyTest {

  private final CapabilityGovernancePolicy policy = new CapabilityGovernancePolicy();
  private static final OwnerRef OWNER = OwnerRef.of("u-1", "张三");

  @Test
  void 未登记能力拒绝() {
    GovernanceDecision d = policy.denyUnknown(new CapabilityCode("MISSING"));
    assertThat(d.allowed()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(GovernanceDecision.REASON_UNKNOWN_CAPABILITY);
    assertThat(d.effectiveState()).isEqualTo(CapabilityState.UNKNOWN);
  }

  @Test
  void 登记但未启用拒绝() {
    AiCapability c = AiCapability.register(new CapabilityCode("M2"), "合同审查", OWNER);
    GovernanceDecision d = policy.decide(c);
    assertThat(d.allowed()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(GovernanceDecision.REASON_INDETERMINATE);
  }

  @Test
  void 可用态放行但恒带AI仅辅助() {
    AiCapability c = AiCapability.register(new CapabilityCode("M2"), "合同审查", OWNER);
    c.enable("admin", "启用");
    GovernanceDecision d = policy.decide(c);
    assertThat(d.allowed()).isTrue();
    assertThat(d.reasonCode()).isEqualTo(GovernanceDecision.REASON_ALLOWED);
    assertThat(d.aiOnlyAdvisory()).isTrue();
  }

  @Test
  void 停用态拒绝() {
    AiCapability c = AiCapability.register(new CapabilityCode("M4"), "辅助评标", OWNER);
    c.enable("admin", "启用");
    c.disable("admin", "停机");
    GovernanceDecision d = policy.decide(c);
    assertThat(d.allowed()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(GovernanceDecision.REASON_DISABLED);
  }

  @Test
  void 降级态拒绝() {
    AiCapability c = AiCapability.register(new CapabilityCode("M4"), "辅助评标", OWNER);
    c.enable("admin", "启用");
    c.degrade("admin", "漂移");
    assertThat(policy.decide(c).reasonCode()).isEqualTo(GovernanceDecision.REASON_DEGRADED);
    assertThat(policy.decide(c).allowed()).isFalse();
  }

  @Test
  void 不可判定态拒绝且不压成可用() {
    AiCapability c = AiCapability.register(new CapabilityCode("G16"), "自治车道", OWNER);
    c.markUndetermined("admin", "待法务");
    GovernanceDecision d = policy.decide(c);
    assertThat(d.allowed()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(GovernanceDecision.REASON_INDETERMINATE);
    assertThat(d.effectiveState()).isEqualTo(CapabilityState.UNDETERMINED);
  }

  @Test
  void 空能力对象返回null交由调用方拒绝() {
    assertThat(policy.decide(null)).isNull();
  }
}
