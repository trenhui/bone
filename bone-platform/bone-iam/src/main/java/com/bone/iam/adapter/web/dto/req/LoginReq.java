package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class LoginReq {
    private String username;
    private String password;
}