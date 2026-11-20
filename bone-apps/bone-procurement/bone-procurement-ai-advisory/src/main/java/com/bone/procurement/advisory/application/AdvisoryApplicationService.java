package com.bone.procurement.advisory.application;

import com.bone.procurement.advisory.domain.model.advisory.AdvisoryCapability;
import com.bone.procurement.advisory.domain.model.advisory.AdvisoryResponse;
import com.bone.procurement.advisory.domain.model.advisory.AiAvailability;
import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.SafetyApplicationService;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 业务 AI 建议裁决（纯工程骨架）：把治理模块的三态裁决 + 全域 kill switch 汇总为一个建议态响应。
 *
 * <p>不注入任何 LLM 网关：本服务不产生模型输出、不声明效果达标；真实模型接入与效果声明属 owner（D-15/D-16/D-17）。
 */
@Service
public class AdvisoryApplicationService {

  private final AiGovernanceApplicationService governance;
  private final SafetyApplicationService safety;

  public AdvisoryApplicationService(
      AiGovernanceApplicationService governance, SafetyApplicationService safety) {
    this.governance = governance;
    this.safety = safety;
  }

  public AdvisoryResponse advise(String rawCapability) {
    AdvisoryCapability cap = AdvisoryCapability.parse(rawCapability);
    if (cap == null) {
      return denied(null, null, "UNKNOWN_CAPABILITY");
    }
    // 全域总闸关闭：一键停用后任何能力不得给出 AI 参与态，主链路走 rules_only。
    if (!safety.globalAllowsAi()) {
      return denied(cap, "GLOBAL_KILL_SWITCH_ENGAGED", "GLOBAL_KILL_SWITCH_ENGAGED");
    }
    GovernanceDecision decision = governance.decide(cap.governanceCode());
    if (!decision.allowed()) {
      return denied(cap, decision.reasonCode(), decision.reasonCode());
    }
    // 合规建议态：显式 + 隐式双标识；证据只给引用（不回传 output 原文，禁 PII）。
    return new AdvisoryResponse(
        cap.codeValue(),
        cap.displayName(),
        AiAvailability.AVAILABLE,
        true,
        null,
        "AI 辅助建议（显式标识）",
        "ai-generated=true;capability=" + cap.codeValue(),
        "advisory-ref://" + cap.codeValue() + "/" + Instant.now().toEpochMilli(),
        GovernanceDecision.REASON_ALLOWED,
        AdvisoryResponse.DISCLOSURE,
        Instant.now());
  }

  /** 缺省拒绝：未登记 / 停用 / 降级 / 总闸关闭统一走这里，绝不 fail-open。 */
  private AdvisoryResponse denied(AdvisoryCapability cap, String reason, String code) {
    return new AdvisoryResponse(
        cap == null ? "UNKNOWN" : cap.codeValue(),
        cap == null ? "未知能力" : cap.displayName(),
        AiAvailability.UNKNOWN,
        true,
        "rules_only",
        "规则建议（显式标识）",
        "ai-generated=false;fallback=rules_only",
        null,
        code == null ? "UNKNOWN_CAPABILITY" : code,
        AdvisoryResponse.DISCLOSURE,
        Instant.now());
  }
}
