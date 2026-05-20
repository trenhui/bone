package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class CreateTenantCmd {
    private String name;
    private String code;
    private Integer level;
    private String adminEmail;
}
