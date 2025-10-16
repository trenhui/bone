package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 代码生成列配置请求
 */
@Data
@Schema(description = "代码生成列配置请求")
public class CodegenColumnRequest {
    @Schema(description = "主键ID")
    private Long id;
    
    @Schema(description = "表ID", example = "1")
    @NotNull(message = "表ID不能为空")
    private Long tableId;
    
    @Schema(description = "列名", example = "user_name")
    @NotBlank(message = "列名不能为空")
    @Size(max = 255, message = "列名长度不能超过255个字符")
    private String columnName;
    
    @Schema(description = "数据类型", example = "varchar")
    @NotBlank(message = "数据类型不能为空")
    @Size(max = 50, message = "数据类型长度不能超过50个字符")
    private String dataType;
    
    @Schema(description = "列描述", example = "用户名")
    @Size(max = 500, message = "列描述长度不能超过500个字符")
    private String columnComment;
    
    @Schema(description = "是否允许为空")
    private Boolean nullable;
    
    @Schema(description = "是否主键")
    private Boolean primaryKey;
    
    @Schema(description = "排序")
    private Integer ordinalPosition;
    
    @Schema(description = "Java类型", example = "String")
    @NotBlank(message = "Java类型不能为空")
    @Size(max = 50, message = "Java类型长度不能超过50个字符")
    private String javaType;
    
    @Schema(description = "Java属性名", example = "userName")
    @NotBlank(message = "Java属性名不能为空")
    @Size(max = 100, message = "Java属性名长度不能超过100个字符")
    private String javaField;
    
    @Schema(description = "字典类型")
    @Size(max = 100, message = "字典类型长度不能超过100个字符")
    private String dictType;
    
    @Schema(description = "示例值", example = "admin")
    @Size(max = 255, message = "示例值长度不能超过255个字符")
    private String example;
    
    @Schema(description = "是否创建字段")
    private Boolean createOperation;
    
    @Schema(description = "是否更新字段")
    private Boolean updateOperation;
    
    @Schema(description = "是否查询字段")
    private Boolean listOperation;
    
    @Schema(description = "查询条件类型", example = "eq")
    @Size(max = 20, message = "查询条件类型长度不能超过20个字符")
    private String listOperationCondition;
    
    @Schema(description = "是否查询结果字段")
    private Boolean listOperationResult;
    
    @Schema(description = "HTML类型", example = "input")
    @Size(max = 50, message = "HTML类型长度不能超过50个字符")
    private String htmlType;
    

}
