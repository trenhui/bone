package com.bone.claim.domain.model.inspection;

/** 差异类型（§9.5 {@code diff_type}，受控码；禁自由文本——差异归因要能聚合出「哪条规则/哪个模型在漏」）。 */
public enum InspectionDiffType {
  /** 理算差异：赔付金额与条款/责任配置口径不一致。 */
  ADJUST_MISMATCH,
  /** 规则漏命中：应拦未拦（如等待期、免责条款）。 */
  RULE_MISS,
  /** AI 误判：模型结论与人工复核相反。 */
  AI_MISJUDGE,
  /** 额度异常：占额/确认/释放与案件不一致。 */
  QUOTA_ABNORMAL,
  /** 无差异（{@link InspectionResult#PASS} 唯一合法取值）。 */
  NONE
}
