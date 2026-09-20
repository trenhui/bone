package com.bone.iam.application.command.cmd;

import lombok.Data;

/** 授予（或变更）用户在应用内的权限。{@code role} 为对外小写表示。 */
@Data
public class GrantAppPermissionCommand {
  private Long appId;
  private Long userId;
  private String role;
}
