package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class CreateRoleCmd {
    private String name;
    private String description;
    private Long tenantId;
}