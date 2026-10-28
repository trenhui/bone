package com.bone.claim.domain.model.inspection;

/** 抽检结论（§9.5 {@code result}，与 Rulebook 后督口径一致）：{@code PASS} 复检一致 / {@code DIFF} 存在差异。 */
public enum InspectionResult {
  PASS,
  DIFF
}
