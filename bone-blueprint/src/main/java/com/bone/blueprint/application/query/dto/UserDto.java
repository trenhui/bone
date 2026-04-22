package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.user.vo.UserId;
import com.bone.blueprint.domain.model.user.vo.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户DTO
 * <p>
 * 用于查询用户信息的DTO
 * </p>
 */
@Data
public class UserDto {
    /**
     * 用户ID
     */
    private UserId id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 状态
     */
    private UserStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}