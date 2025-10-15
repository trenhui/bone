package com.bone.demo.application.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询DTO")
public class UserPageQuery extends UserQuery {
    /**
     * 页码，从1开始
     */
    @Schema(description = "页码，从1开始", requiredMode = Schema.RequiredMode.REQUIRED, defaultValue = "1")
    private Integer pageNum = 1;
    
    /**
     * 每页大小
     */
    @Schema(description = "每页大小", requiredMode = Schema.RequiredMode.REQUIRED, defaultValue = "10")
    private Integer pageSize = 10;
}