package com.bone.procurement.fulfillment.domain.port;

/** Finance 只读 ACL 端口（InvoiceMatched / PaymentRecorded INBOUND）。默认 fail-closed。 */
public interface FinanceAclPort {

  /** 上游不可用一律返回 false；发票保持 MATCH_PENDING，不推断匹配通过（M5-11）。 */
  default boolean isAvailable() {
    return false;
  }
}
