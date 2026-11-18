package com.bone.procurement.fulfillment.domain.port;

/** Inventory 只读 ACL 端口（GoodsReceived INBOUND）。默认 fail-closed，运行期不触发真实上游。 */
public interface InventoryAclPort {

  /** 上游不可用（未交付/未配置/异常）一律返回 false；调用方据此保持待态、不推断收货。 */
  default boolean isAvailable() {
    return false;
  }
}
