package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    
    @Schema(description = "模块名", example = "system")
    @NotNull(message = "模块名不能为空")
    @Size(max = 50, message = "模块名长度不能超过50个字符")
    private String moduleName;
    
    @Schema(description = "包路径", example = "com.bone.system")
    @NotNull(message = "包路径不能为空")
    @Size(max = 255, message = "包路径长度不能超过255个字符")
    private String packageName;
}
