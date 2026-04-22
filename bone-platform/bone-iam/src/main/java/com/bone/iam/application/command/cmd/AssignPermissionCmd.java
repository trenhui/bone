package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class AssignPermissionCmd {
    private Long roleId;
    private Long[] permissionIds;
}