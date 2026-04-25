package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class ResetPasswordCmd {
    private Long id;
    private String newPassword;
}
