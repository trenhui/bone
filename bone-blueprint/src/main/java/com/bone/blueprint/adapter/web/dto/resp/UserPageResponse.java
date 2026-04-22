package com.bone.blueprint.adapter.web.dto.resp;

import com.bone.blueprint.domain.model.user.vo.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户分页响应DTO
 * <p>
 * 用于返回用户列表的HTTP响应
 * </p>
 */
@Data
public class UserPageResponse {
    /**
     * 用户ID
     */
    private String id;
    
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
}