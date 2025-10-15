package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 导入表请求
 * <p>
 * 用于从数据库导入表结构的应用层DTO
 */
@Schema(description = "导入表请求")
@Data
public class ImportTableRequest {
    
    @Schema(description = "数据源配置ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "数据源配置ID不能为空")
    private Long dataSourceConfigId;
    
    @Schema(description = "表名列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"sys_user\",\"sys_role\"]")
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;
    
    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "system")
    @NotBlank(message = "模块名称不能为空")
    @Size(max = 50, message = "模块名称长度不能超过50个字符")
    private String moduleName;
    
    @Schema(description = "包路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.bone.system")
    @NotBlank(message = "包路径不能为空")
    @Size(max = 255, message = "包路径长度不能超过255个字符")
    private String packageName;
    
    @Schema(description = "生成场景，1: 单表 2: 主从表 3: 树表", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "生成场景不能为空")
    private Integer scene;
    
    @Schema(description = "模板类型，1: SaaS模式 2: DDD领域模型", example = "1")
    private Integer modelType;
}