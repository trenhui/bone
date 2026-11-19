package com.bone.procurement.governance.domain.service;

import com.bone.procurement.governance.domain.model.fairness.BiasSignal;
import com.bone.procurement.governance.domain.model.fairness.FairnessDecision;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 确定性偏见检测规则（G15 纯工程部分）。
 *
 * <p>不依赖模型：按受控特征与 AI 建议文本做确定性检查，命中即 fail-closed 阻断。这是「调试/回归用确定性护栏」，不是统计公平性度量（后者需 ≥200 样本与 owner
 * 通过线，属 backlog）。
 *
 * <p>规则：
 *
 * <ol>
 *   <li>任一特征 UNKNOWN → 信号不可判定 → 转人工（不压成无偏见）；
 *   <li>AI 建议文本含所有制偏好词（国企优先/民营优先/SOE preferred 等）→ 所有制偏见 → 阻断晋升 + 伦理审查（FAIR-01）；
 *   <li>含地域偏好词（华东优先/某地优先等）→ 地域偏见 → 转人工不发建议（FAIR-02）；
 *   <li>含规模偏好词（大企业优先等）→ 规模偏见 → 转人工；
 *   <li>否则 clear（仅建议、人工终决）。
 * </ol>
 */
public final class FairnessRule {

  private static final Set<String> OWNERSHIP_BIAS_TOKENS =
      Set.of("国企优先", "民营优先", "所有制优先", "soe preferred", "foreign preferred", "国企加分", "民企加分");
  private static final Set<String> REGION_BIAS_TOKENS =
      Set.of("华东优先", "华北优先", "本地优先", "region preferred", "某地优先", "异地排除");
  private static final Set<String> SCALE_BIAS_TOKENS =
      Set.of("大企业优先", "大厂优先", "规模加分", "small excluded", "小企业排除");

  /** 按信号裁决；FaIR-01/02 的判定完全由此确定性规则产生。 */
  public FairnessDecision evaluate(BiasSignal signal) {
    Objects.requireNonNull(signal, "signal");
    if (signal.ownership() == BiasSignal.OwnershipType.UNKNOWN
        || signal.region() == BiasSignal.RegionTier.UNKNOWN
        || signal.scale() == BiasSignal.ScaleTier.UNKNOWN) {
      return FairnessDecision.indeterminate(signal.subjectRef());
    }
    String text =
        signal.aiSuggestion() == null ? "" : signal.aiSuggestion().toLowerCase(Locale.ROOT);
    if (containsAny(text, OWNERSHIP_BIAS_TOKENS)) {
      return FairnessDecision.ownershipBias(signal.subjectRef());
    }
    if (containsAny(text, REGION_BIAS_TOKENS)) {
      return FairnessDecision.regionalBias(signal.subjectRef());
    }
    if (containsAny(text, SCALE_BIAS_TOKENS)) {
      return FairnessDecision.scaleBias(signal.subjectRef());
    }
    return FairnessDecision.clear(signal.subjectRef());
  }

  private static boolean containsAny(String text, Set<String> tokens) {
    return tokens.stream().anyMatch(text::contains);
  }
}
