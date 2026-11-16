package com.bone.masterdata.application.query.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 供应商当前状态（治理端只读视图）。
 *
 * <p>只暴露统一身份、两套互不覆盖的状态与版本、以及最近的迁移摘要（状态 + 事件码 + 时间），<b>不含任何字段原值</b>。
 */
@Data
@Builder
public class SupplierStateDTO {

  private String partyId;

  private String supplierState;

  private long supplierVersion;

  private String vendorState;

  private long vendorVersion;

  /** 最近的迁移摘要：{@code from->to (event)}，最新在后。 */
  private List<String> supplierHistory;

  private List<String> vendorHistory;
}
