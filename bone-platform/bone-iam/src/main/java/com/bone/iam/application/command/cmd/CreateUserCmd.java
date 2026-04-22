package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class CreateUserCmd {
    private String username;
    private String password;
    private String email;
    private Long tenantId;
    private Long[] roleIds;
}