package com.bone.tool.codegen;

import com.bone.tool.codegen.domain.entity.CodegenColumn;

public class TestMain {
    public static void main(String[] args) {
        CodegenColumn column = new CodegenColumn();
        column.setId(1L);
        column.setColumnName("test_column");
        column.setDataType("VARCHAR");
        System.out.println("CodegenColumn created: " + column);
    }
}