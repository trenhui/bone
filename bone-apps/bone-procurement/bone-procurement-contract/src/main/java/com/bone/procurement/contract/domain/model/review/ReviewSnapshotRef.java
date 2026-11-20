package com.bone.procurement.contract.domain.model.review;

/** 审查快照引用：只存引用，绝不存合同原文或 output 正文。 */
public record ReviewSnapshotRef(String ref) {

  public static ReviewSnapshotRef of(String ref) {
    if (ref == null || ref.isBlank()) {
      throw new IllegalArgumentException("审查快照引用不得为空");
    }
    return new ReviewSnapshotRef(ref);
  }
}
