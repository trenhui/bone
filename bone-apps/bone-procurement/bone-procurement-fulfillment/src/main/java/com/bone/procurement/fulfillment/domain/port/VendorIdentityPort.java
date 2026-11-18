package com.bone.procurement.fulfillment.domain.port;

import java.util.Optional;

/**
 * 供应商身份端口：从可信来源（安全上下文）解析当前请求的供应商 partyId。
 *
 * <p>服务层一律经此端口取身份，不接受客户端传入的 vendorPartyId（M5-1 越权 fail-closed）。默认实现返回 empty， 由基础设施层从 Spring
 * Security 解析并覆盖。
 */
public interface VendorIdentityPort {

  Optional<String> currentVendorPartyId();

  /** 默认拒绝：无实现时任何写操作都拿不到身份 ⇒ 403。 */
  default String requireVendorPartyId() {
    return currentVendorPartyId()
        .filter(v -> !v.isBlank())
        .orElseThrow(() -> new IllegalStateException("NO_VENDOR_IDENTITY"));
  }
}
