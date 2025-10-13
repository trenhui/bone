package com.bone.tools.codegen.application.dto;

import cn.hutool.core.util.ObjectUtil;
import com.bone.tools.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 代码生成表配置 保存请求
 * <p>
 * 用于创建或更新代码生成表配置的应用层DTO
 */
@Schema(description = "代码生成表配置保存请求")
@Data
public class CodegenTableSaveRequest {

    @Schema(description = "表配置ID，创建时无需填写，更新时必填", example = "1")
    private Long id;

    @Schema(description = "数据库表名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "sys_user")
    private String tableName;

    @Schema(description = "数据库表描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户信息表")
    private String tableComment;

    @Schema(description = "备注信息", example = "用于存储系统用户基本信息")
    private String remark;

    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "system")
    private String moduleName;

    @Schema(description = "Java包名", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.bone.system")
    private String packgeName;

    @Schema(description = "业务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "user")
    private String businessName;

    @Schema(description = "Java类名", requiredMode = Schema.RequiredMode.REQUIRED, example = "SysUser")
    private String className;

    @Schema(description = "Java类描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "用户信息")
    private String classComment;

    @Schema(description = "作者", requiredMode = Schema.RequiredMode.REQUIRED, example = "bone")
    private String author;

    @Schema(description = "模板类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer templateType;

    @Schema(description = "前端类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    private Integer frontType;

    @Schema(description = "主表配置ID，用于子表场景", example = "2048")
    private Long masterTableId;
    
    @Schema(description = "子表关联主表的字段ID", example = "4096")
    private Long subJoinColumnId;
    
    @Schema(description = "是否一对多关系", example = "true")
    private Boolean subJoinMany;

    @Schema(description = "树表父字段ID", example = "8192")
    private Long treeParentColumnId;
    
    @Schema(description = "树表名称字段ID", example = "16384")
    private Long treeNameColumnId;

    /**
     * 验证父菜单ID是否有效（当前版本暂不启用）
     */
    @JsonIgnore
    public boolean isParentMenuIdValid() {
        return true;
    }

    /**
     * 验证子表配置是否完整
     */
    @JsonIgnore
    public boolean isSubConfigValid() {
        return ObjectUtil.notEqual(getTemplateType(), CodegenTemplateTypeEnum.SUB)
                || (ObjectUtil.isAllNotEmpty(masterTableId, subJoinColumnId, subJoinMany));
    }

    /**
     * 验证树表配置是否完整
     */
    @JsonIgnore
    public boolean isTreeConfigValid() {
        return ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.TREE)
                || (ObjectUtil.isAllNotEmpty(treeParentColumnId, treeNameColumnId));
    }

}
