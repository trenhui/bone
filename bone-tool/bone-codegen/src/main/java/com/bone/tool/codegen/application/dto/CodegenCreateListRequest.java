package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 批量创建代码生成配置请求
 */
@Schema(description = "批量创建代码生成配置请求")
@Data
public class CodegenCreateListRequest {

    @Schema(description = "数据源配置ID", example = "1")
    @NotNull(message = "数据源配置ID不能为空")
    private Long dataSourceConfigId;

    @Schema(description = "表名列表", example = "[\"sys_user\",\"sys_role\"]")
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;
}
