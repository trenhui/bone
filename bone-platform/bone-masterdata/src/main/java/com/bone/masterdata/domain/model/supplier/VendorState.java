package com.bone.masterdata.domain.model.supplier;

/**
 * Vendor 交易与付款资格生命周期状态（LLD `01-供应商主数据模块详细设计.md` §5）。
 *
 * <p><b>Owner 边界</b>：本状态机承载财务侧 Company Code data（银行、税务、付款条件、付款冻结），Owner = 财务。 采购域可以读取与推进普通迁移，但
 * {@code PAYMENT_BLOCKED} 的解除只能由外部财务事件发起（见 {@link TransitionCommandSource}）。
 *
 * <p><b>与 {@link SupplierState} 的关系</b>：两套状态机并行、互不覆盖，无任何共享常量。
 */
public enum VendorState {

  /** 待开通：已具备合作资格，交易付款闸门未跑完。 */
  PENDING_VENDOR("PENDING_VENDOR"),

  /** 已开通交易付款资格。 */
  ACTIVE_VENDOR("ACTIVE_VENDOR"),

  /** 付款冻结：财务/风控事件触发，本域不得自行放行。 */
  PAYMENT_BLOCKED("PAYMENT_BLOCKED"),

  /** 已停用：交易付款资格终态，重新开通须重走全部闸门。 */
  INACTIVE_VENDOR("INACTIVE_VENDOR");

  private final String code;

  VendorState(String code) {
    this.code = code;
  }

  /** 对外契约码（大小写与 LLD/spec 一致）。 */
  public String code() {
    return code;
  }

  /** 终态：迁移表中无任何 from 行；重新开通必须新建一轮闸门，不能直接回写。 */
  public boolean isTerminal() {
    return this == INACTIVE_VENDOR;
  }
}
