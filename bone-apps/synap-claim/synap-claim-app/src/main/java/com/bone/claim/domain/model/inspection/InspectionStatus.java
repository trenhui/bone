package com.bone.claim.domain.model.inspection;

/**
 * 抽检单状态（只前进，不可回退）。
 *
 * <p>{@code PENDING}（待抽检）→ {@code IN_PROGRESS}（抽检中，已领取）→ {@code CONCLUDED}（已结论，终态）。
 *
 * <p><b>为何结论是终态</b>：后督结论是审计证据，改写会使「抽检 → 差异 → 处置」链路失去可追溯性；需要更正时另开一次抽检（二期 放开一案多检），而不是覆盖原结论。
 */
public enum InspectionStatus {
  PENDING,
  IN_PROGRESS,
  CONCLUDED;

  public boolean terminal() {
    return this == CONCLUDED;
  }
}
