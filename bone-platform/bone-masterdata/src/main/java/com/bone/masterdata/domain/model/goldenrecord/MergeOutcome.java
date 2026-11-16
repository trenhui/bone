package com.bone.masterdata.domain.model.goldenrecord;

/** 合并裁决结果：只有 APPLIED 允许进入分发链路，其余一律转人工。 */
public enum MergeOutcome {
  APPLIED,
  MANUAL_REVIEW_REQUIRED,
  REJECTED
}
