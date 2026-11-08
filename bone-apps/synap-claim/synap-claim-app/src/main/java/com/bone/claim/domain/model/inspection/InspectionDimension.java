package com.bone.claim.domain.model.inspection;

/** 抽检维度（后督关注面，§7.6）：金额准确性 / 单证完整性 / 医疗合理性 / 流程时效。 */
public enum InspectionDimension {
  /** 理算金额与条款口径是否一致（与 {@link InspectionDiffType#ADJUST_MISMATCH} 对应）。 */
  AMOUNT("金额准确性"),
  /** 单证是否齐全、锁定后是否被篡改。 */
  DOCUMENT("单证完整性"),
  /** 医疗必要性/目录库适用性（需 C11 医学目录，一期多为人工判定）。 */
  MEDICAL("医疗合理性"),
  /** 时效与流程合规（超期、越权操作）。 */
  PROCESS("流程时效");

  private final String displayName;

  InspectionDimension(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
