package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 自定义代码生成请求
 * <p>
 * 用于灵活配置数据源、表选择和代码生成参数的应用层DTO
 */
@Schema(description = "自定义代码生成请求")
@Data
public class GenerateCustomCodeRequest {
    
    @Schema(description = "数据源配置ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "数据源配置ID不能为空")
    private Long dataSourceConfigId;
    
    @Schema(description = "表名列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"user\",\"order\"]")
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;
    
    @Schema(description = "工程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "demo-project")
    @NotEmpty(message = "工程名称不能为空")
    @Size(max = 100, message = "工程名称长度不能超过100个字符")
    private String projectName;
    
    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "system")
    @NotEmpty(message = "模块名称不能为空")
    @Size(max = 50, message = "模块名称长度不能超过50个字符")
    private String moduleName;
    
    @Schema(description = "基础包路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.example")
    @NotEmpty(message = "基础包路径不能为空")
    @Size(max = 255, message = "基础包路径长度不能超过255个字符")
    private String basePackage;
    
    @Schema(description = "模板类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "saas", allowableValues = {"saas", "ddd"})
    @NotEmpty(message = "模板类型不能为空")
    private String modelType;
    
    @Schema(description = "生成场景", requiredMode = Schema.RequiredMode.REQUIRED, example = "single", allowableValues = {"single", "batch"})
    @NotEmpty(message = "生成场景不能为空")
    private String scene;
    
    @Schema(description = "作者", example = "bone-team")
    @Size(max = 50, message = "作者姓名长度不能超过50个字符")
    private String author;
    
    @Schema(description = "移除表前缀", example = "t_,sys_")
    @Size(max = 255, message = "表前缀长度不能超过255个字符")
    private String tablePrefix;
    
    @Schema(description = "是否生成前端代码", example = "true")
    private Boolean generateFrontend = true;
    
    @Schema(description = "是否生成数据库脚本", example = "true")
    private Boolean generateSqlScript = true;
    
    @Schema(description = "是否覆盖已有文件", example = "false")
    private Boolean overrideExisting = false;
}