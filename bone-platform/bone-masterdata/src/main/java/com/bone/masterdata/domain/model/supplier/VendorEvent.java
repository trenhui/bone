package com.bone.masterdata.domain.model.supplier;

/**
 * Vendor 交易与付款资格生命周期事件（LLD `01 卷 §5`）。
 *
 * <p>{@link #PAYMENT_BLOCK_RELEASED} 只能由外部财务事件发起（{@link TransitionCommandSource#EXTERNAL_FINANCE}），
 * 域内来源一律拒绝 —— 与「AI 不碰钱」同构：能力存在但默认不可达。
 */
public enum VendorEvent {

  /** 交易付款闸门全项通过（准入 / 银行核验 / 税务 / 付款条件 / 重复发票检查）。 */
  ACTIVATION_PASSED("activation_passed"),

  /** 财务或风控事件触发付款冻结。 */
  PAYMENT_BLOCK_RAISED("payment_block_raised"),

  /** 付款冻结解除：仅接受外部财务事件。 */
  PAYMENT_BLOCK_RELEASED("payment_block_released"),

  /** 停用：不得留下进行中的采购。 */
  DEACTIVATE("deactivate");

  private final String code;

  VendorEvent(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
