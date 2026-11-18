package com.bone.procurement.fulfillment.domain.port;

import java.math.BigDecimal;

/** 发票 TDS 容差配置端口（按供应商/品类，禁止硬编码阈值）。默认严格容差 0。 */
public interface ToleranceConfigPort {

  /** 返回该供应商+品类允许的 TDS 差额上限（含边界）；未配置返回 0（严格）。 */
  default BigDecimal tolerance(Long tenantId, String vendorPartyId, String category) {
    return BigDecimal.ZERO;
  }
}
