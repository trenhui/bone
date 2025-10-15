package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据库表响应
 * <p>
 * 用于返回数据库表信息的应用层DTO
 */
@Schema(description = "数据库表响应")
@Data
public class DatabaseTableResponse {

    @Schema(description = "表名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_user")
    private String name;

    @Schema(description = "表描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户信息表")
    private String comment;

}
