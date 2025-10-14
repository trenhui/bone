package com.bone.tool.codegen.application.dto;

import cn.hutool.core.util.ObjectUtil;
import com.bone.tool.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 代码生成表配置 保存请求
 * <p>
 * 用于创建或更新代码生成表配置的应用层DTO
 */
@Schema(description = "代码生成表配置保存请求")
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
    
    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableComment() {
        return tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getPackgeName() {
        return packgeName;
    }

    public void setPackgeName(String packgeName) {
        this.packgeName = packgeName;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getClassComment() {
        return classComment;
    }

    public void setClassComment(String classComment) {
        this.classComment = classComment;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Integer getTemplateType() {
        return templateType;
    }

    public void setTemplateType(Integer templateType) {
        this.templateType = templateType;
    }
}
