package com.bone.demo.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户DTO
 */
@Data
@Schema(description = "用户DTO")
public class UserDTO {
    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private Long id;
    
    /**
     * 用户名
     */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    
    /**
     * 密码
     */
    @Schema(description = "密码")
    private String password;
    
    /**
     * 昵称
     */
    @Schema(description = "昵称")
    private String nickname;
    
    /**
     * 状态：0-禁用 1-启用
     */
    @Schema(description = "状态：0-禁用 1-启用")
    private Integer status;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private Long createTime;
    
    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private Long updateTime;
}