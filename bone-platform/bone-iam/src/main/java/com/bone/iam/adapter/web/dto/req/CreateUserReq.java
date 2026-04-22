package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class CreateUserReq {
    private String username;
    private String password;
    private String email;
    private Long tenantId;
    private Long[] roleIds;
}