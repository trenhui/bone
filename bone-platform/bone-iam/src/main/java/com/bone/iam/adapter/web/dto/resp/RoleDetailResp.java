package com.bone.iam.adapter.web.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleDetailResp {
    private Long id;
    private String name;
    private String description;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long[] permissionIds;
}