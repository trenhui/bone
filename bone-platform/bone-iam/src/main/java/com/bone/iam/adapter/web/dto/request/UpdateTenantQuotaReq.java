package com.bone.iam.adapter.web.dto.request;

import lombok.Data;

@Data
public class UpdateTenantQuotaReq {
  /** 账号数上限，{@code null} 表示不限制。 */
  private Integer maxAccounts;

  /** 角色数上限，{@code null} 表示不限制。 */
  private Integer maxRoles;
}
