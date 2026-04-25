package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateRoleCmd {
    private Long id;
    private String name;
    private String description;
}

