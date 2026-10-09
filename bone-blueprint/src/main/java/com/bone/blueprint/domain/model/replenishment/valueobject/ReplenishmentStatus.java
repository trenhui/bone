package com.bone.blueprint.domain.model.replenishment.valueobject;

/**
 * 补货单状态机。
 *
 * <p>流转：{@code DRAFT → SUBMITTED → APPROVED → RECEIVED}，旁路 {@code CANCELLED}（DRAFT/SUBMITTED）。
 *
 * <p><b>为何拆四步</b>：供应链补货有「计划 → 采购确认 → 到货入库」决策点；合成一步会把「未审批就入库」变成默许，掩盖账实责任。
 */
public enum ReplenishmentStatus {
  /** 草稿（可改数量/供应商）。 */
  DRAFT,
  /** 已提交采购（待审批）。 */
  SUBMITTED,
  /** 已批准（待到货）。 */
  APPROVED,
  /** 已到货入库（终态）。 */
  RECEIVED,
  /** 已取消（终态）。 */
  CANCELLED;

  public boolean canSubmit() {
    return this == DRAFT;
  }

  public boolean canApprove() {
    return this == SUBMITTED;
  }

  public boolean canReceive() {
    return this == APPROVED;
  }

  public boolean canCancel() {
    return this == DRAFT || this == SUBMITTED;
  }

  public boolean isTerminal() {
    return this == RECEIVED || this == CANCELLED;
  }
}
