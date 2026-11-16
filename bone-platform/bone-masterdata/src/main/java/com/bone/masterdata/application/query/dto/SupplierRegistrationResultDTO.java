package com.bone.masterdata.application.query.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 供应商登记结果。
 *
 * <p><b>不含字段原值或 PII</b>：只回裁决、双角色状态、版本与字段数量。身份主键（统一社会信用代码 / 税号）与银行信息一律不出现在响应里 （spec
 * Scenario「统一响应包装」）。
 */
@Data
@Builder
public class SupplierRegistrationResultDTO {

  /** APPLIED / MANUAL_REVIEW_REQUIRED / REJECTED（沿用 Golden Record 内核裁决枚举名）。 */
  private String outcome;

  private String partyId;

  /** Supplier 合作资格状态码（PROSPECT 等）。 */
  private String supplierState;

  /** Vendor 交易付款资格状态码（PENDING_VENDOR 等）。 */
  private String vendorState;

  private long supplierVersion;

  private long vendorVersion;

  /** 黄金记录胜出字段数；未成立时为 0。 */
  private int fieldCount;

  private String reason;
}
