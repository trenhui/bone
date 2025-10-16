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

    @Schema(description = "表ID", example = "1")
    private Long tableId;

    @Schema(description = "列名", example = "user_name")
    private String columnName;

    @Schema(description = "数据类型", example = "varchar")
    private String dataType;

    @Schema(description = "列描述", example = "用户名")
    private String columnComment;

    @Schema(description = "是否允许为空")
    private Boolean nullable;

    @Schema(description = "是否主键")
    private Boolean primaryKey;

    @Schema(description = "排序")
    private Integer ordinalPosition;

    @Schema(description = "Java类型", example = "String")
    private String javaType;

    @Schema(description = "Java属性名", example = "userName")
    private String javaField;

    @Schema(description = "字典类型")
    private String dictType;

    @Schema(description = "示例值", example = "admin")
    private String example;

    @Schema(description = "是否创建字段")
    private Boolean createOperation;

    @Schema(description = "是否更新字段")
    private Boolean updateOperation;

    @Schema(description = "是否查询字段")
    private Boolean listOperation;

    @Schema(description = "查询条件类型", example = "eq")
    private String listOperationCondition;

    @Schema(description = "是否查询结果字段")
    private Boolean listOperationResult;

    @Schema(description = "HTML类型", example = "input")
    private String htmlType;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
