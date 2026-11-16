package com.bone.masterdata.application.query.dto;

import lombok.Builder;
import lombok.Data;

/** 状态迁移结果：只回状态变化与版本，不回传字段原值。 */
@Data
@Builder
public class SupplierTransitionResultDTO {

  private String partyId;

  /** 角色标识：{@code SUPPLIER} 或 {@code VENDOR}。 */
  private String role;

  private String fromState;

  private String toState;

  private long version;

  /** 守卫依据（原因码），便于审计与客户端提示。 */
  private String basis;
}
