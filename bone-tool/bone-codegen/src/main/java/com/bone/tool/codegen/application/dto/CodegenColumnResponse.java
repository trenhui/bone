package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码生成列响应DTO
 * 用于返回代码生成中的列配置信息
 *
 * @author bone-team
 */
@Data
@Schema(description = "代码生成列配置响应信息")
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

    @Schema(description = "是否为主键")
    private Boolean primaryKey;

    @Schema(description = "排序位置")
    private Integer ordinalPosition;

    @Schema(description = "Java类型", example = "String")
    private String javaType;

    @Schema(description = "Java字段名", example = "userName")
    private String javaField;

    @Schema(description = "字典类型")
    private String dictType;

    @Schema(description = "示例值", example = "admin")
    private String example;

    @Schema(description = "是否创建操作")
    private Boolean createOperation;

    @Schema(description = "是否更新操作")
    private Boolean updateOperation;

    @Schema(description = "是否列表操作")
    private Boolean listOperation;

    @Schema(description = "列表操作条件", example = "eq")
    private String listOperationCondition;

    @Schema(description = "列表操作结果")
    private Boolean listOperationResult;

    @Schema(description = "HTML类型", example = "input")
    private String htmlType;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
