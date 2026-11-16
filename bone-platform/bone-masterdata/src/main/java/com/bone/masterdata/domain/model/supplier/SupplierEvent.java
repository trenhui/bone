package com.bone.masterdata.domain.model.supplier;

/**
 * Supplier 合作资格生命周期事件（LLD `01 卷 §5`）。
 *
 * <p>事件码与 spec 中的小写写法规格一致（{@link #code()}），枚举名用大写以符合 Java 惯例。
 */
public enum SupplierEvent {

  /** 提交准入申请：背调与资质核验启动。 */
  ONBOARDING_SUBMITTED("onboarding_submitted"),

  /** 准入批准：变更请求全段批准，且至少存在 1 个已开通的 Vendor 角色。 */
  ONBOARDING_APPROVED("onboarding_approved"),

  /** 风险事件或绩效不达标 → 暂停合作。 */
  RISK_EXCEEDED("risk_exceeded"),

  /** 需限期整改 → 进入整改态（与暂停区分：整改态可限定范围继续履约）。 */
  CORRECTIVE_REQUIRED("corrective_required"),

  /** 整改复核通过 → 恢复合作。 */
  RESTORED("restored"),

  /** 退出批准：零未结 PO、未结合同、未清应付，且全部 Vendor 已停用。 */
  EXIT_APPROVED("exit_approved");

  private final String code;

  SupplierEvent(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
