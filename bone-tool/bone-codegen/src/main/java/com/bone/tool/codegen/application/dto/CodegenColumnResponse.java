package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码生成列配置响应
 */
@Data
@Schema(description = "代码生成列配置响应")
public class CodegenColumnResponse {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "所属表ID", example = "1")
    private Long tableId;

    @Schema(description = "数据库列名", example = "user_name")
    private String columnName;

    @Schema(description = "数据库数据类型", example = "varchar")
    private String dataType;

    @Schema(description = "列注释", example = "用户名")
    private String columnComment;

    @Schema(description = "Java数据类型", example = "String")
    private String javaType;

    @Schema(description = "Java字段名", example = "userName")
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
    private String listQueryCondition;

    @Schema(description = "HTML表单控件类型", example = "input")
    private String htmlType;

    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "关联表名")
    private String relationTableName;

    @Schema(description = "关联表展示字段")
    private String relationShowField;

    @Schema(description = "关联表查询字段")
    private String relationQueryField;

    @Schema(description = "扩展属性，JSON格式")
    private String extraAttrs;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "更新人")
    private String updatedBy;
}
