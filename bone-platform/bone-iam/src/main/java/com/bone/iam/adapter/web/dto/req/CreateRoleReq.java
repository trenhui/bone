package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class CreateRoleReq {
    private String name;
    private String code;
    private String description;
    private Long tenantId;
}