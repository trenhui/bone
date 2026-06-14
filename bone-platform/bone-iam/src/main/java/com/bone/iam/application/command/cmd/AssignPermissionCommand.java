package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class AssignPermissionCommand {
  private Long roleId;
  private Long[] permissionIds;
}
