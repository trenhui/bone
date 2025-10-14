package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理后台 - 代码生成字段定义创建/修改 Request VO")
public class CodegenColumnSaveRequest {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "表编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long tableId;

    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "user_age")
    private String columnName;

    @Schema(description = "字段类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "int(11)")
    private String dataType;

    @Schema(description = "字段描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "年龄")
    private String columnComment;

    @Schema(description = "是否允许为空", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean nullable;

    @Schema(description = "是否主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean primaryKey;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer ordinalPosition;

    @Schema(description = "Java 属性类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "userAge")
    private String javaType;

    @Schema(description = "Java 属性名", requiredMode = Schema.RequiredMode.REQUIRED, example = "Integer")
    private String javaField;

    @Schema(description = "字典类型", example = "sys_gender")
    private String dictType;

    @Schema(description = "数据示例", example = "1024")
    private String example;

    @Schema(description = "是否为 Create 创建操作的字段", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean createOperation;

    @Schema(description = "是否为 Update 更新操作的字段", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean updateOperation;

    @Schema(description = "是否为 List 查询操作的字段", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean listOperation;

    @Schema(description = "List 查询操作的条件类型，参见 CodegenColumnListConditionEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "LIKE")
    private String listOperationCondition;

    @Schema(description = "是否为 List 查询操作的返回字段", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean listOperationResult;

    @Schema(description = "显示类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "input")
    private String htmlType;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public Boolean getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(Boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Integer getOrdinalPosition() {
        return ordinalPosition;
    }

    public void setOrdinalPosition(Integer ordinalPosition) {
        this.ordinalPosition = ordinalPosition;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getJavaField() {
        return javaField;
    }

    public void setJavaField(String javaField) {
        this.javaField = javaField;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public Boolean getCreateOperation() {
        return createOperation;
    }

    public void setCreateOperation(Boolean createOperation) {
        this.createOperation = createOperation;
    }

    public Boolean getUpdateOperation() {
        return updateOperation;
    }

    public void setUpdateOperation(Boolean updateOperation) {
        this.updateOperation = updateOperation;
    }

    public Boolean getListOperation() {
        return listOperation;
    }

    public void setListOperation(Boolean listOperation) {
        this.listOperation = listOperation;
    }

    public String getListOperationCondition() {
        return listOperationCondition;
    }

    public void setListOperationCondition(String listOperationCondition) {
        this.listOperationCondition = listOperationCondition;
    }

    public Boolean getListOperationResult() {
        return listOperationResult;
    }

    public void setListOperationResult(Boolean listOperationResult) {
        this.listOperationResult = listOperationResult;
    }

    public String getHtmlType() {
        return htmlType;
    }

    public void setHtmlType(String htmlType) {
        this.htmlType = htmlType;
    }
}
