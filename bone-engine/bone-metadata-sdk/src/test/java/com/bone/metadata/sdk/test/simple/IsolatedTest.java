package com.bone.metadata.sdk.test.simple;

/**
 * 完全隔离的测试类，不依赖任何项目类或外部测试框架
 * 用于测试测试环境是否正常
 */
public class IsolatedTest {
    
    /**
     * 简化的断言工具方法
     */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("断言通过: " + message);
    }
    
    private static void assertEquals(Object expected, Object actual, String message) {
        boolean isEqual = (expected == null && actual == null) || 
                          (expected != null && expected.equals(actual));
        if (!isEqual) {
            throw new AssertionError(message + " [期望值: " + expected + ", 实际值: " + actual + "]");
        }
        System.out.println("断言通过: " + message);
    }
    
    /**
     * 测试简单断言
     */
    public void testSimpleAssertion() {
        // 只使用简单的断言，不涉及任何项目代码
        boolean condition = true;
        assertTrue(condition, "简单断言应该通过");
    }
    
    /**
     * 测试字符串相等
     */
    public void testStringEquality() {
        String expected = "test";
        String actual = "test";
        assertEquals(expected, actual, "字符串应该相等");
    }
    
    /**
     * 测试算术运算
     */
    public void testArithmetic() {
        int result = 2 + 2;
        assertEquals(4, result, "2 + 2 应该等于 4");
    }
    
    /**
     * 主方法，运行所有测试
     */
    public static void main(String[] args) {
        System.out.println("开始运行IsolatedTest...");
        IsolatedTest test = new IsolatedTest();
        
        try {
            test.testSimpleAssertion();
            test.testStringEquality();
            test.testArithmetic();
            System.out.println("所有测试通过！");
        } catch (AssertionError e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}