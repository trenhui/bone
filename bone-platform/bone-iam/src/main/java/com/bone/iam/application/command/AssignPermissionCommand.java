package com.bone.iam.application.command;

import lombok.Data;

@Data
public class AssignPermissionCommand {
  private Long roleId;
  private Long[] permissionIds;
}
