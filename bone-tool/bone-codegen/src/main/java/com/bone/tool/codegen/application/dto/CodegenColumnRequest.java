package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 代码生成列配置请求
 * 用于创建或更新代码生成列配置的应用层DTO
 *
 * @author bone-team
 */
@Data
@Schema(description = "代码生成列配置请求")
public class CodegenColumnRequest {
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "表ID不能为空")
    private Long tableId;
    
    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "user_name")
    @NotBlank(message = "字段名不能为空")
    @Size(max = 255, message = "字段名长度不能超过255个字符")
    private String columnName;
    
    @Schema(description = "字段类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "varchar")
    @NotBlank(message = "字段类型不能为空")
    @Size(max = 50, message = "字段类型长度不能超过50个字符")
    private String dataType;
    
    @Schema(description = "字段描述", example = "用户名")
    @Size(max = 500, message = "字段描述长度不能超过500个字符")
    private String columnComment;
    
    @Schema(description = "是否允许为空")
    private Boolean nullable;
    
    @Schema(description = "是否主键")
    private Boolean primaryKey;
    
    @Schema(description = "排序")
    private Integer ordinalPosition;
    
    @Schema(description = "Java 属性类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "String")
    @NotBlank(message = "Java类型不能为空")
    @Size(max = 50, message = "Java类型长度不能超过50个字符")
    private String javaType;
    
    @Schema(description = "Java 属性名", requiredMode = Schema.RequiredMode.REQUIRED, example = "userName")
    @NotBlank(message = "Java字段名不能为空")
    @Size(max = 100, message = "Java字段名长度不能超过100个字符")
    private String javaField;
    
    @Schema(description = "字典类型")
    @Size(max = 100, message = "字典类型长度不能超过100个字符")
    private String dictType;
    
    @Schema(description = "数据示例", example = "admin")
    @Size(max = 255, message = "数据示例长度不能超过255个字符")
    private String example;
    
    @Schema(description = "是否为 Create 创建操作的字段")
    private Boolean createOperation;
    
    @Schema(description = "是否为 Update 更新操作的字段")
    private Boolean updateOperation;
    
    @Schema(description = "是否为 List 查询操作的字段")
    private Boolean listOperation;
    
    @Schema(description = "List 查询操作的条件类型", example = "eq")
    @Size(max = 20, message = "查询操作条件长度不能超过20个字符")
    private String listOperationCondition;
    
    @Schema(description = "是否为 List 查询操作的返回字段")
    private Boolean listOperationResult;
    
    @Schema(description = "显示类型", example = "input")
    @Size(max = 50, message = "显示类型长度不能超过50个字符")
    private String htmlType;
    

}
