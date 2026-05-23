package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateTenantCommand {
    private Long id;
    private String name;
    private Integer level;
    private String adminEmail;
}
