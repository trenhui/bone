package com.bone.blueprint.application.query.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户查询响应DTO
 * <p>
 * 用于返回用户查询结果的DTO
 * </p>
 */
@Data
public class UserQueryResp {
    /**
     * 用户ID
     */
    private Long id;
    
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
    private Integer status;
    
    /**
     * 状态描述
     */
    private String statusDescription;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}