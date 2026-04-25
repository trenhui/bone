package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateAccountCmd {
    private Long id;
    private String email;
    private String phone;
    private String realName;
    private Integer status;
    private Long[] roleIds;
}
