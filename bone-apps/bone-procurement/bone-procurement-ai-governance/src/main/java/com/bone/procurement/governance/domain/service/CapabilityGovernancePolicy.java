package com.bone.procurement.governance.domain.service;

import com.bone.procurement.governance.domain.model.capability.AiCapability;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityState;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;

/**
 * 能力准入裁决策略（纯领域、无 IO）：把能力登记状态翻译成 fail-closed 裁决。
 *
 * <p>关键不变量：
 *
 * <ul>
 *   <li>未登记能力 → 拒绝（不可对未知能力放行）；
 *   <li>UNKNOWN / UNDETERMINED → 拒绝（不可判定态不得压成可用）；
 *   <li>DISABLED / DEGRADED → 拒绝（停机/降级优先）；
 *   <li>只有 AVAILABLE 允许进入 AI 推理路径，且裁决恒带「AI 仅辅助、人工终决」语义。
 * </ul>
 */
public final class CapabilityGovernancePolicy {

  /** 对已登记能力裁决；未登记返回 null，交由调用方按 UNKNOWN_CAPABILITY 拒绝。 */
  public GovernanceDecision decide(AiCapability capability) {
    if (capability == null) {
      return null;
    }
    CapabilityCode code = capability.code();
    CapabilityState state = capability.state();
    if (state.isIndeterminate()) {
      return GovernanceDecision.deny(code, state, GovernanceDecision.REASON_INDETERMINATE);
    }
    if (state == CapabilityState.DISABLED) {
      return GovernanceDecision.deny(code, state, GovernanceDecision.REASON_DISABLED);
    }
    if (state == CapabilityState.DEGRADED) {
      return GovernanceDecision.deny(code, state, GovernanceDecision.REASON_DEGRADED);
    }
    return GovernanceDecision.allow(code);
  }

  /** 未登记能力的统一拒绝裁决。 */
  public GovernanceDecision denyUnknown(CapabilityCode code) {
    return GovernanceDecision.deny(
        code, CapabilityState.UNKNOWN, GovernanceDecision.REASON_UNKNOWN_CAPABILITY);
  }
}
