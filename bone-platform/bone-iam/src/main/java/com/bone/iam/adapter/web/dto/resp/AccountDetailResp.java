package com.bone.iam.adapter.web.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccountDetailResp {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String realName;
    private String avatarUrl;
    private Integer status;
    private Boolean isAdmin;
    private Long[] roleIds;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
