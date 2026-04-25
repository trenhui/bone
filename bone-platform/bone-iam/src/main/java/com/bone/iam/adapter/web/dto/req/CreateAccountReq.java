package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class CreateAccountReq {
    private String username;
    private String password;
    private String email;
    private String phone;
    private String realName;
    private Long tenantId;
    private Long[] roleIds;
}
