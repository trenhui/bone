package com.bone.iam.application.query.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccountDTO {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String realName;
    private String avatarUrl;
    /** 0=禁用, 1=启用, 2=锁定 */
    private Integer status;
    private Boolean isAdmin;
    private Long tenantId;
    /** 绑定的角色 ID 列表（来自 iam_account_role） */
    private Long[] roleIds;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
