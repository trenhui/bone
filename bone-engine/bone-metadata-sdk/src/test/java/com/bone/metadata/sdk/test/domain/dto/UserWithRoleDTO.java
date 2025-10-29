package com.bone.metadata.sdk.test.domain.dto;

import lombok.Data;

import java.util.Date;

@Data
public class UserWithRoleDTO {
    private Long id;
    private String name;
    private Long roleId;
    private String roleName;
    private String roleDescription;
    private Date createTime;
    private Long createBy;
    private Date updateTime;
    private Long updateBy;
    private Boolean deleted;
}
