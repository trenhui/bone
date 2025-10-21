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
    
    @Schema(description = "所属表ID", example = "1")
    @NotNull(message = "表ID不能为空")
    private Long tableId;
    
    @Schema(description = "数据库列名", example = "user_name")
    @NotBlank(message = "列名不能为空")
    @Size(max = 255, message = "列名长度不能超过255个字符")
    private String columnName;
    
    @Schema(description = "数据库数据类型", example = "varchar")
    @NotBlank(message = "数据类型不能为空")
    @Size(max = 50, message = "数据类型长度不能超过50个字符")
    private String dataType;
    
    @Schema(description = "列注释", example = "用户名")
    @Size(max = 500, message = "列描述长度不能超过500个字符")
    private String columnComment;
    
    @Schema(description = "Java数据类型", example = "String")
    @NotBlank(message = "Java类型不能为空")
    @Size(max = 50, message = "Java类型长度不能超过50个字符")
    private String javaType;
    
    @Schema(description = "Java字段名", example = "userName")
    @NotBlank(message = "Java属性名不能为空")
    @Size(max = 100, message = "Java属性名长度不能超过100个字符")
    private String javaField;
    
    @Schema(description = "是否主键")
    private Boolean primaryKey;
    
    @Schema(description = "是否自增")
    private Boolean autoIncrement;
    
    @Schema(description = "是否可为空")
    private Boolean nullable;
    
    @Schema(description = "是否用于创建操作")
    private Boolean enableCreate;
    
    @Schema(description = "是否用于更新操作")
    private Boolean enableUpdate;
    
    @Schema(description = "是否用于列表查询")
    private Boolean enableQuery;
    
    @Schema(description = "是否在列表结果中展示")
    private Boolean showInList;
    
    @Schema(description = "列表查询条件类型", example = "eq")
    @Size(max = 20, message = "查询条件类型长度不能超过20个字符")
    private String listQueryCondition;
    
    @Schema(description = "HTML表单控件类型", example = "input")
    @Size(max = 50, message = "HTML类型长度不能超过50个字符")
    private String htmlType;
    
    @Schema(description = "字典类型编码")
    @Size(max = 100, message = "字典类型长度不能超过100个字符")
    private String dictType;
    
    @Schema(description = "关联表名")
    private String relationTableName;
    
    @Schema(description = "关联表展示字段")
    private String relationShowField;
    
    @Schema(description = "关联表查询字段")
    private String relationQueryField;
    
    @Schema(description = "扩展属性，JSON格式")
    private String extraAttrs;
    

}
