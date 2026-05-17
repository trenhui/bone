package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码生成表配置响应
 */
@Schema(description = "代码生成表配置响应")
@Data
public class CodegenTableResponse {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "生成场景", example = "1")
    private Integer scene;

    @Schema(description = "表名", example = "sys_user")
    private String tableName;

    @Schema(description = "表描述", example = "用户信息表")
    private String tableComment;

    @Schema(description = "备注", example = "我是备注")
    private String remark;

    @Schema(description = "模块名", example = "system")
    private String moduleName;

    @Schema(description = "包路径", example = "com.bone.system")
    private String packageName;

    @Schema(description = "业务名", example = "user")
    private String businessName;

    @Schema(description = "类名", example = "SysUser")
    private String className;

    @Schema(description = "类描述", example = "用户信息")
    private String classComment;

    @Schema(description = "作者", example = "bone")
    private String author;

    @Schema(description = "模板类型", example = "1")
    private Integer templateType;

    @Schema(description = "前端类型", example = "20")
    private Integer frontType;

    @Schema(description = "父菜单ID", example = "1024")
    private Long parentMenuId;

    @Schema(description = "主表ID", example = "2048")
    private Long masterTableId;
    
    @Schema(description = "子表关联字段ID", example = "4096")
    private Long subJoinColumnId;
    
    @Schema(description = "是否一对多", example = "true")
    private Boolean subJoinMany;

    @Schema(description = "树表父字段ID", example = "8192")
    private Long treeParentColumnId;
    
    @Schema(description = "树表名字字段ID", example = "16384")
    private Long treeNameColumnId;

    @Schema(description = "数据源ID", example = "1024")
    private Long datasourceId;

    @Schema(description = "数据源名称")
    private String dataSourceConfigName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    private String createdAtStr;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    private String updatedAtStr;
}
