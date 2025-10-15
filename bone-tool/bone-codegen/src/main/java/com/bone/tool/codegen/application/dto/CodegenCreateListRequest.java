package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 批量创建代码生成配置请求
 * <p>
 * 基于数据库表结构批量创建代码生成器配置的应用层DTO
 */
@Schema(description = "批量创建代码生成配置请求")
@Data
public class CodegenCreateListRequest {

    @Schema(description = "数据源配置ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "数据源配置ID不能为空")
    private Long dataSourceConfigId;

    @Schema(description = "表名列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"sys_user\",\"sys_role\"]")
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;

}
