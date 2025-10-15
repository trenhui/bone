package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 自定义代码生成请求参数
 * <p>
 * 支持灵活配置数据源、表选择和代码生成参数
 * 
 * @author bone-team
 */
@Data
@Schema(description = "自定义代码生成请求参数")
public class GenerateCustomCodeRequest {
    
    @Schema(description = "数据源配置ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "数据源配置ID不能为空")
    private Long dataSourceConfigId;
    
    @Schema(description = "表名列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"user\",\"order\"]")
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;
    
    @Schema(description = "工程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "demo-project")
    @NotEmpty(message = "工程名称不能为空")
    private String projectName;
    
    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "system")
    @NotEmpty(message = "模块名称不能为空")
    private String moduleName;
    
    @Schema(description = "基础包路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.example")
    @NotEmpty(message = "基础包路径不能为空")
    private String basePackage;
    
    @Schema(description = "模板类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "saas", allowableValues = {"saas", "ddd"})
    @NotEmpty(message = "模板类型不能为空")
    private String modelType;
    
    @Schema(description = "生成场景", requiredMode = Schema.RequiredMode.REQUIRED, example = "single", allowableValues = {"single", "batch"})
    @NotEmpty(message = "生成场景不能为空")
    private String scene;
    
    @Schema(description = "作者", example = "bone-team")
    private String author;
    
    @Schema(description = "移除表前缀", example = "t_,sys_")
    private String tablePrefix;
    
    @Schema(description = "是否生成前端代码", example = "true")
    private Boolean generateFrontend = true;
    
    @Schema(description = "是否生成数据库脚本", example = "true")
    private Boolean generateSqlScript = true;
    
    @Schema(description = "是否覆盖已有文件", example = "false")
    private Boolean overrideExisting = false;
}