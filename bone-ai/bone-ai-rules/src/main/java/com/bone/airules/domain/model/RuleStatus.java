package com.bone.airules.domain.model;

/** 规则资产生命周期状态。 */
public enum RuleStatus {
  DRAFT, // 草稿（未发布，不可被执行）
  ACTIVE, // 生效（可被执行；灰度期按 grayPercent 命中）
  DEPRECATED // 停用（不再执行，版本链保留供回放）
}
