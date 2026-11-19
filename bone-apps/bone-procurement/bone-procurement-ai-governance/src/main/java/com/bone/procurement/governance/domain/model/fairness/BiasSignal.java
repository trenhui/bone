package com.bone.procurement.governance.domain.model.fairness;

import java.util.Objects;

/**
 * 偏见信号输入（G15 / D3-v2 §3「算法公平与反歧视」）。
 *
 * <p>确定性规则检查 AI 辅助选商/评标/推荐是否按<b>所有制 / 地域 / 规模</b>产生系统性差异。这里只承载结构化特征与 AI 建议， 由 {@code FairnessRule}
 * 做确定性裁决；不保存真实供应商名称 / PII，特征值均为受控枚举或脱敏分档。
 */
public record BiasSignal(
    String subjectRef,
    OwnershipType ownership,
    RegionTier region,
    ScaleTier scale,
    String aiSuggestion,
    double aiScore) {

  /** 所有制受控枚举（合成常量，无真实企业名）。 */
  public enum OwnershipType {
    SOE,
    PRIVATE,
    FOREIGN,
    OTHER,
    UNKNOWN
  }

  /** 地域分档（脱敏，不用真实地名）。 */
  public enum RegionTier {
    EAST,
    CENTRAL,
    WEST,
    UNKNOWN
  }

  /** 规模分档（脱敏）。 */
  public enum ScaleTier {
    LARGE,
    MEDIUM,
    SMALL,
    UNKNOWN
  }

  public BiasSignal {
    Objects.requireNonNull(subjectRef, "subjectRef");
    Objects.requireNonNull(ownership, "ownership");
    Objects.requireNonNull(region, "region");
    Objects.requireNonNull(scale, "scale");
    if (aiSuggestion == null) {
      aiSuggestion = "";
    }
  }
}
