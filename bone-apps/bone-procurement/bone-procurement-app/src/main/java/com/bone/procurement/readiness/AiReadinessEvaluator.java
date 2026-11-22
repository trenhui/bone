package com.bone.procurement.readiness;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 就绪度判词评估器（纯函数、无 Spring 依赖）。
 *
 * <p>规则（fail-closed）：
 *
 * <ul>
 *   <li>任一事实为 null（取数失败/未知）→ UNKNOWN，保留原始码；
 *   <li>治理 Bean 未装配 → UNKNOWN（缺省拒绝，无法判定 AI 能力状态）；
 *   <li>provider 未配 / 总闸生效 / 评测未冻结 / 无已启用可推理能力 → 至少 DEGRADED，绝不 AVAILABLE；
 *   <li>五类事实全部通过 → AVAILABLE。
 * </ul>
 */
@Component
public class AiReadinessEvaluator {

  public AiReadinessView evaluate(AiReadinessFacts f) {
    List<String> reasons = new ArrayList<>();

    if (f.providerConfigured() == null) {
      reasons.add("PROVIDER_STATE_UNKNOWN");
    }
    if (f.killSwitchEngaged() == null) {
      reasons.add("KILL_SWITCH_STATE_UNKNOWN");
    }
    if (f.evalFrozen() == null) {
      reasons.add("EVAL_STATE_UNKNOWN");
    }
    if (!reasons.isEmpty()) {
      return view(AiReadinessVerdict.UNKNOWN, f, reasons);
    }

    if (!f.governanceEnabled()) {
      reasons.add("AI_GOVERNANCE_DISABLED");
      return view(AiReadinessVerdict.UNKNOWN, f, reasons);
    }
    if (!f.providerConfigured()) {
      reasons.add("PROVIDER_NOT_CONFIGURED");
    }
    if (f.killSwitchEngaged()) {
      reasons.add("GLOBAL_KILL_SWITCH_ENGAGED");
    }
    if (!f.evalFrozen()) {
      reasons.add("EVAL_NOT_FROZEN");
    }
    if (!f.inferenceCapabilityRegistered()) {
      reasons.add("INFERENCE_CAPABILITY_NOT_REGISTERED");
    }

    AiReadinessVerdict verdict =
        reasons.isEmpty() ? AiReadinessVerdict.AVAILABLE : AiReadinessVerdict.DEGRADED;
    return view(verdict, f, reasons);
  }

  private static AiReadinessView view(
      AiReadinessVerdict verdict, AiReadinessFacts f, List<String> reasons) {
    boolean gatesPassed = verdict == AiReadinessVerdict.AVAILABLE;
    return new AiReadinessView(
        verdict,
        verdict.name(),
        Boolean.TRUE.equals(f.providerConfigured()),
        f.governanceEnabled(),
        Boolean.TRUE.equals(f.killSwitchEngaged()),
        Boolean.TRUE.equals(f.evalFrozen()),
        f.inferenceCapabilityRegistered(),
        gatesPassed,
        AiReadinessView.DEFAULT_NOTICE,
        List.copyOf(reasons));
  }
}
