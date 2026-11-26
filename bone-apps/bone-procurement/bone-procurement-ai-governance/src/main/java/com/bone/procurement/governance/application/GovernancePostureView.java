package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.model.capability.PostureVerdict;
import java.util.List;
import java.util.Objects;

/**
 * 治理态势只读投影：单一汇总判词 + 能力明细（仅结构化判词与计数，不含 reason 原文 / AI output / PII）。
 *
 * @param verdict 汇总态势判词
 * @param capabilities 能力明细（字段白名单，见 {@link CapabilityPostureEntry}）
 * @param attentionCount 需 owner 复核的能力数
 * @param undeterminedCount 含不可判定分项的能力数
 * @param modelHealthClaim 恒为 NONE——不声明模型效果达标
 */
public record GovernancePostureView(
    String verdict,
    List<CapabilityPostureEntry> capabilities,
    long attentionCount,
    long undeterminedCount,
    String modelHealthClaim) {

  public static final String NO_MODEL_HEALTH_CLAIM = "NONE";

  /** 能力明细：只含判词/状态/计数相关字段，禁止 reason 原文、AI output、PII。 */
  public record CapabilityPostureEntry(
      String code,
      String displayName,
      String state,
      boolean decisionAllowed,
      String decisionReasonCode,
      String driftVerdict,
      String oversightVerdict,
      boolean requiresOwnerReview) {}

  public GovernancePostureView {
    Objects.requireNonNull(verdict, "verdict");
    capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }

  public static GovernancePostureView from(
      PostureVerdict verdict, List<CapabilityPostureEntry> capabilities) {
    List<CapabilityPostureEntry> entries =
        capabilities == null ? List.of() : List.copyOf(capabilities);
    long attention = entries.stream().filter(CapabilityPostureEntry::requiresOwnerReview).count();
    long undetermined =
        entries.stream()
            .filter(
                e ->
                    "UNDETERMINED".equals(e.driftVerdict())
                        || "UNDETERMINED".equals(e.oversightVerdict()))
            .count();
    return new GovernancePostureView(
        verdict.name(), entries, attention, undetermined, NO_MODEL_HEALTH_CLAIM);
  }
}
