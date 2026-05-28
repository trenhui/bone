package com.bone.iam.application.query.dto;

import lombok.Data;

@Data
public class TenantDTO {
    private Long id;
    private String name;
    private String code;
    private Integer level;
    private Integer status;
    private String adminEmail;
    private Integer maxAccounts;
    private Integer maxRoles;
}
