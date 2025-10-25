package com.bone.metadata.sdk.test.simple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完全隔离的测试类，不依赖任何项目类
 * 只使用JUnit框架，用于测试测试环境是否正常
 */
public class IsolatedTest {
    
    @Test
    public void testSimpleAssertion() {
        // 只使用简单的断言，不涉及任何项目代码
        boolean condition = true;
        assertTrue(condition, "简单断言应该通过");
    }
    
    @Test
    public void testStringEquality() {
        String expected = "test";
        String actual = "test";
        assertEquals(expected, actual, "字符串应该相等");
    }
    
    @Test
    public void testArithmetic() {
        int result = 2 + 2;
        assertEquals(4, result, "2 + 2 应该等于 4");
    }
}