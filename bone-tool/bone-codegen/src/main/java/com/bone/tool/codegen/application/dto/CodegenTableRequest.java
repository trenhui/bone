package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 代码生成表配置请求
 */
@Schema(description = "代码生成表配置请求")
@Data
public class CodegenTableRequest {

    @Schema(description = "主键ID，更新时必填", example = "1")
    private Long id;

    @Schema(description = "表名", example = "sys_user")
    @NotBlank(message = "表名不能为空")
    @Size(max = 255, message = "表名长度不能超过255个字符")
    private String tableName;

    @Schema(description = "表描述", example = "用户信息表")
    @NotBlank(message = "表描述不能为空")
    @Size(max = 500, message = "表描述长度不能超过500个字符")
    private String tableComment;

    @Schema(description = "模块名", example = "system")
    @NotBlank(message = "模块名不能为空")
    @Size(max = 50, message = "模块名长度不能超过50个字符")
    private String moduleName;

    @Schema(description = "包路径", example = "com.bone.system")
    @NotBlank(message = "包路径不能为空")
    @Size(max = 255, message = "包路径长度不能超过255个字符")
    private String packageName;

    @Schema(description = "业务名", example = "user")
    @NotBlank(message = "业务名不能为空")
    @Size(max = 50, message = "业务名长度不能超过50个字符")
    private String businessName;

    @Schema(description = "类名", example = "SysUser")
    @NotBlank(message = "类名不能为空")
    @Size(max = 100, message = "类名长度不能超过100个字符")
    private String className;

    @Schema(description = "类描述", example = "用户信息")
    @NotBlank(message = "类描述不能为空")
    @Size(max = 255, message = "类描述长度不能超过255个字符")
    private String classComment;

    @Schema(description = "作者", example = "bone")
    @NotBlank(message = "作者不能为空")
    @Size(max = 50, message = "作者长度不能超过50个字符")
    private String author;

    @Schema(description = "模板类型", example = "1")
    @NotNull(message = "模板类型不能为空")
    private Integer templateType;

    @Schema(description = "列配置列表")
    private List<CodegenColumnRequest> columns;

    /**
     * 判断是否为更新操作
     * @return 是否为更新操作
     */
    public boolean isUpdate() {
        return id != null;
    }
}