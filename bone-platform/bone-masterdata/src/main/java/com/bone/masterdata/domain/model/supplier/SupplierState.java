package com.bone.masterdata.domain.model.supplier;

/**
 * Supplier 合作资格生命周期状态（LLD `01-供应商主数据模块详细设计.md` §5）。
 *
 * <p><b>与 {@link VendorState} 的关系</b>：两套状态机并行、互不覆盖，无任何共享常量。同一 legalEntity 可以同时处于 Suppliers 的 {@code
 * SUSPENDED} 与 Vendor 的 {@code ACTIVE_VENDOR}，任一侧的结果不得用于推导另一侧。
 */
public enum SupplierState {

  /** 潜在供应商：已识别主体，尚未进入准入流程。 */
  PROSPECT("PROSPECT"),

  /** 准入中：背调与资质核验进行中。 */
  ONBOARDING("ONBOARDING"),

  /** 合作中：可寻源、可签合同。 */
  ACTIVE("ACTIVE"),

  /** 已暂停：风险事件或绩效不达标触发，暂停新增合作。 */
  SUSPENDED("SUSPENDED"),

  /** 整改中：限定范围内继续履约并接受整改跟踪。 */
  CORRECTIVE("CORRECTIVE"),

  /** 已退出：合作资格终态，不可回写。 */
  EXITED("EXITED");

  private final String code;

  SupplierState(String code) {
    this.code = code;
  }

  /** 对外契约码（大小写与 LLD/spec 一致）。 */
  public String code() {
    return code;
  }

  /** 终态：迁移表中无任何 from 行，任何事件一律拒绝。 */
  public boolean isTerminal() {
    return this == EXITED;
  }
}
