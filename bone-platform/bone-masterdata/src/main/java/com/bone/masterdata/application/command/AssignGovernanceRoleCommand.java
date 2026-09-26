package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 治理角色指派命令（G3）。roleType：OWNER / STEWARD / APPROVER。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignGovernanceRoleCommand {

  @NotNull(message = "masterDataEntityId: 主数据实体ID不能为空")
  private Long masterDataEntityId;

  @NotNull(message = "accountId: IAM 账号ID不能为空")
  private Long accountId;

  @NotBlank(message = "roleType: 治理角色不能为空")
  private String roleType;
}
