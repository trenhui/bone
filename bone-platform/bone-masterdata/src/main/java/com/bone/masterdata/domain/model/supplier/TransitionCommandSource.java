package com.bone.masterdata.domain.model.supplier;

/**
 * 迁移命令来源。
 *
 * <p><b>为什么需要它</b>：{@link VendorState#PAYMENT_BLOCKED} 属财务侧 Owner，采购域不得自行解除。仅靠「调用方声称有权限」不足以守住边界 ——
 * 命令必须显式声明来源，域内来源调用解除事件一律拒绝（fail-closed）。
 */
public enum TransitionCommandSource {

  /** 采购域内发起（平台事件、采购操作）。 */
  DOMAIN,

  /** 外部财务系统事件（付款冻结解除的唯一合法来源）。 */
  EXTERNAL_FINANCE
}
