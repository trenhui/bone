package com.bone.blueprint.domain.model.shipment.valueobject;

/**
 * 发货单状态机。
 *
 * <p>流转：{@code CREATED → SHIPPED → IN_TRANSIT → SIGNED}，旁路 {@code FAILED}。
 *
 * <p><b>为何 CREATED 与 SHIPPED 分开</b>：CREATED 表示「已生成发货单但尚未交运」， SHIPPED
 * 表示「已拿到运单号并回传渠道」。二者分开才能区分「仓库未拣货」与「已交运」， 也才能对渠道回传失败做重试（回传失败不回退状态，只置 FAILED 并记录原因）。
 */
public enum ShipmentStatus {
  /** 已创建（待发货）。 */
  CREATED,
  /** 已发货（已生成运单号）。 */
  SHIPPED,
  /** 运输中。 */
  IN_TRANSIT,
  /** 已签收。 */
  SIGNED,
  /** 发货失败（渠道拒收/地址不可达等），{@code failReason} 必填。 */
  FAILED;

  /** 是否可发货（生成运单并回传渠道）。 */
  public boolean canShip() {
    return this == CREATED || this == FAILED;
  }

  /** 是否可签收。 */
  public boolean canSign() {
    return this == SHIPPED || this == IN_TRANSIT;
  }

  /** 是否已终结（不可再迁移）。 */
  public boolean isTerminal() {
    return this == SIGNED;
  }
}
