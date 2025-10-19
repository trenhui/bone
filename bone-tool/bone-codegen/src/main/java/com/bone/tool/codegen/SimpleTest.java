package com.bone.tool.codegen;

/**
 * 简单测试类，用于验证编译环境
 */
public class SimpleTest {
    public static void main(String[] args) {
        System.out.println("简单测试开始...");
        
        // 测试基本功能
        testBasicFunctionality();
        
        System.out.println("简单测试完成！");
    }
    
    private static void testBasicFunctionality() {
        System.out.println("测试基本功能模块");
        System.out.println("1. 字符串操作测试: " + ("test".toUpperCase().equals("TEST")));
        System.out.println("2. 数值计算测试: " + (2 + 2 == 4));
        System.out.println("3. 数组操作测试: " + testArray());
    }
    
    private static boolean testArray() {
        int[] arr = {1, 2, 3, 4, 5};
        return arr.length == 5 && arr[0] == 1 && arr[4] == 5;
    }
}