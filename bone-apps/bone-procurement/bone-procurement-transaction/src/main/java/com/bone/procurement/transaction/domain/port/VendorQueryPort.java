package com.bone.procurement.transaction.domain.port;

import java.util.Optional;

/** 供应商交易资格查询端口（transaction 侧定义，避免 domain 反向依赖主数据）。 */
public interface VendorQueryPort {

  /**
   * 查询供应商在指定租户下的交易资格快照。返回 empty 表示供应商不存在。
   *
   * <p>实现须 fail-closed：查询异常/未配置一律视为不可用，不得返回 ACTIVE。
   */
  Optional<VendorSnapshot> findSnapshot(Long tenantId, String partyId);

  /** 只读快照：只含交易闸门所需字段，不含 PII。 */
  record VendorSnapshot(String partyId, String vendorState, Long legalEntityId) {

    public boolean isActiveVendor() {
      return "ACTIVE_VENDOR".equals(vendorState);
    }
  }
}
