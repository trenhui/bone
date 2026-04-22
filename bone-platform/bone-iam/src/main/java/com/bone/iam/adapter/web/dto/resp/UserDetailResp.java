package com.bone.iam.adapter.web.dto.resp;

import com.bone.iam.domain.model.user.vo.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDetailResp {
    private Long id;
    private String username;
    private String email;
    private UserStatus status;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long[] roleIds;
}