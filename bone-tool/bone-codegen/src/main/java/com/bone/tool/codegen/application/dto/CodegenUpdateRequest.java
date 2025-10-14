package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "管理后台 - 代码生成表和字段的修改 Request VO")
public class CodegenUpdateRequest {

    private CodegenTableSaveRequest table;
    private List<CodegenColumnSaveRequest> columns;
    
    public CodegenTableSaveRequest getTable() {
        return table;
    }
    
    public void setTable(CodegenTableSaveRequest table) {
        this.table = table;
    }
    
    public List<CodegenColumnSaveRequest> getColumns() {
        return columns;
    }
    
    public void setColumns(List<CodegenColumnSaveRequest> columns) {
        this.columns = columns;
    }
}
