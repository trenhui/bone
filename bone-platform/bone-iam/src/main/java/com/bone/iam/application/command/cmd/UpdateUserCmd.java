package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class UpdateUserCmd {
    private String id;
    private String email;
    private Long[] roleIds;
}