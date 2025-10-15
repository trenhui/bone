package com.bone.demo.application.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户查询DTO
 */
@Data
@Schema(description = "用户查询DTO")
public class UserQuery {
    /**
     * 用户名（模糊查询）
     */
    @Schema(description = "用户名（模糊查询）")
    private String username;
    
    /**
     * 昵称（模糊查询）
     */
    @Schema(description = "昵称（模糊查询）")
    private String nickname;
    
    /**
     * 状态：0-禁用 1-启用
     */
    @Schema(description = "状态：0-禁用 1-启用")
    private Integer status;
}