package com.bone.iam.application.query.dto;

import com.bone.iam.domain.model.user.vo.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDTO {
    private String id;
    private String username;
    private String email;
    private UserStatus status;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}