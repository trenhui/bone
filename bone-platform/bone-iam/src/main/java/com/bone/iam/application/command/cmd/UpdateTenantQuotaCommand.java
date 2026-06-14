package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateTenantQuotaCommand {

  private Long id;

  /** 账号数上限，{@code null} 表示不限制。 */
  private Integer maxAccounts;

  /** 角色数上限，{@code null} 表示不限制。 */
  private Integer maxRoles;
}
