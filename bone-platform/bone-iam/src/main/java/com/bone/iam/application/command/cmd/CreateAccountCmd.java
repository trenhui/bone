package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class CreateAccountCmd {
    private String username;
    private String password;
    private String email;
    private String phone;
    private String realName;
    private Long tenantId;
    private Long[] roleIds;
}
