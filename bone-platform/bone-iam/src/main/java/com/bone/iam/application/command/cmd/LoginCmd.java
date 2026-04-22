package com.bone.iam.application.command.cmd;

import lombok.Data;

@Data
public class LoginCmd {
    private String username;
    private String password;
}