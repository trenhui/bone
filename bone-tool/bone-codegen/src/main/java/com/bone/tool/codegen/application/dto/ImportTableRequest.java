package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 导入表请求
 */
@Data
@Schema(description = "导入表请求")
public class ImportTableRequest {

    @Schema(description = "数据源ID", example = "1")
    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    @Schema(description = "表名列表", example = "[\"sys_user\", \"sys_role\"]")
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;

    @Schema(description = "模块名", example = "system")
    @NotNull(message = "模块名不能为空")
    private String moduleName;

    @Schema(description = "业务名", example = "用户管理")
    @NotNull(message = "业务名不能为空")
    private String businessName;

    @Schema(description = "功能名", example = "用户")
    @NotNull(message = "功能名不能为空")
    private String functionName;

    @Schema(description = "类名", example = "SysUser")
    private String className;

    @Schema(description = "表前缀", example = "sys_")
    private String tablePrefix;

    @Schema(description = "是否覆盖已有配置")
    private Boolean overrideConfig;
}