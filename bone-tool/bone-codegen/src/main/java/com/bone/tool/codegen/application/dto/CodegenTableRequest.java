package com.bone.tool.codegen.application.dto;

import cn.hutool.core.util.ObjectUtil;
import com.bone.tool.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 代码生成表配置 请求
 * <p>
 * 用于创建或更新代码生成表配置及列配置的应用层DTO
 */
@Schema(description = "代码生成表配置请求")
@Data
public class CodegenTableRequest {

    @Schema(description = "表配置ID，创建时无需填写，更新时必填", example = "1")
    private Long id;

    @Schema(description = "数据库表名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_user")
    @NotBlank(message = "数据库表名称不能为空")
    @Size(max = 255, message = "数据库表名称长度不能超过255个字符")
    private String tableName;

    @Schema(description = "数据库表描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户信息表")
    @NotBlank(message = "数据库表描述不能为空")
    @Size(max = 500, message = "数据库表描述长度不能超过500个字符")
    private String tableComment;

    @Schema(description = "备注信息", example = "用于存储系统用户基本信息")
    @Size(max = 500, message = "备注信息长度不能超过500个字符")
    private String remark;

    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "system")
    @NotBlank(message = "模块名称不能为空")
    @Size(max = 50, message = "模块名称长度不能超过50个字符")
    private String moduleName;

    @Schema(description = "Java包名", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.bone.system")
    @NotBlank(message = "Java包名不能为空")
    @Size(max = 255, message = "Java包名长度不能超过255个字符")
    private String packageName;

    @Schema(description = "业务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "user")
    @NotBlank(message = "业务名称不能为空")
    @Size(max = 50, message = "业务名称长度不能超过50个字符")
    private String businessName;

    @Schema(description = "Java类名", requiredMode = Schema.RequiredMode.REQUIRED, example = "SysUser")
    @NotBlank(message = "Java类名不能为空")
    @Size(max = 100, message = "Java类名长度不能超过100个字符")
    private String className;

    @Schema(description = "Java类描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户信息")
    @NotBlank(message = "Java类描述不能为空")
    @Size(max = 255, message = "Java类描述长度不能超过255个字符")
    private String classComment;

    @Schema(description = "作者", requiredMode = Schema.RequiredMode.REQUIRED, example = "bone")
    @NotBlank(message = "作者不能为空")
    @Size(max = 50, message = "作者姓名长度不能超过50个字符")
    private String author;

    @Schema(description = "模板类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "模板类型不能为空")
    private Integer templateType;

    @Schema(description = "列配置信息列表")
    private List<CodegenColumnRequest> columns;

    /**
     * 检查是否为更新操作
     * @return 是否为更新操作
     */
    @JsonIgnore
    public boolean isUpdate() {
        return id != null;
    }
}