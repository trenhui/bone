package com.bone.iam.application.query.dto;

import com.bone.iam.domain.account.vo.AccountStatus;
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
    private AccountStatus status;
    private Boolean isAdmin;
    private Long tenantId;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
