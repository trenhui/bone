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
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
