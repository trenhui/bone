package com.bone.procurement.governance.domain.model.capability;

/**
 * 漂移信号判词（监管 §6/§9 漂移监测闭环）。
 *
 * <p>刻意采用三态而非二态：不可判定时必须显式为 UNDETERMINED，不得压成"无漂移"；即使未触阈也不声明模型健康。
 */
public enum DriftVerdict {
  /** 阈值未配置 / 样本不足 / 计数非法——不可判定（fail-closed）。 */
  UNDETERMINED,
  /** 样本充足且未触阈——仅表示当前观测窗口无 actionable 信号，不构成模型效果背书。 */
  NO_ACTIONABLE_SIGNAL,
  /** 样本充足且 override 率触阈——需 owner 复核与评测扩样，不自动改变任何能力状态。 */
  REVIEW_REQUIRED;

  /** 仅 REVIEW_REQUIRED 需要 owner 采取动作。 */
  public boolean isActionable() {
    return this == REVIEW_REQUIRED;
  }
}
