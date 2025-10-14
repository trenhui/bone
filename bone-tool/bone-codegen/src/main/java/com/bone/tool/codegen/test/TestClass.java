package com.bone.tool.codegen.test;

public class TestClass {
    public void test() {
        try {
            // 使用反射来实例化CodegenColumn类，避免编译时依赖问题
            Class<?> codegenColumnClass = Class.forName("com.bone.tool.codegen.domain.entity.CodegenColumn");
            Object column = codegenColumnClass.getDeclaredConstructor().newInstance();
            System.out.println("Test successful: " + column);
        } catch (Exception e) {
            System.err.println("Error instantiating CodegenColumn: " + e.getMessage());
            e.printStackTrace();
        }
    }
}