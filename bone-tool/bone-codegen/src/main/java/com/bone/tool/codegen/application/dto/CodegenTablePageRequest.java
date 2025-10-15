package com.bone.tool.codegen.application.dto;

import com.bone.core.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 代码生成表配置分页查询请求
 * <p>
 * 用于分页查询代码生成表配置的应用层DTO
 */
@Schema(description = "代码生成表配置分页查询请求")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CodegenTablePageRequest extends PageParam {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Schema(description = "表名称，模糊匹配", example = "sys_user")
    private String tableName;

    @Schema(description = "表描述，模糊匹配", example = "用户信息表")
    private String tableComment;

    @Schema(description = "Java类名，模糊匹配", example = "SysUser")
    private String className;

    @Schema(description = "创建时间范围", example = "[2022-07-01 00:00:00,2022-07-01 23:59:59]")
    @DateTimeFormat(pattern = DATE_TIME_PATTERN)
    private LocalDateTime[] createTime;

    @Schema(description = "开始时间")
    @DateTimeFormat(pattern = DATE_TIME_PATTERN)
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    @DateTimeFormat(pattern = DATE_TIME_PATTERN)
    private LocalDateTime endTime;
}
