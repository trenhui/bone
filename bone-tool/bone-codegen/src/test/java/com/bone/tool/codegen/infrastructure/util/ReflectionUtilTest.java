package com.bone.tool.codegen.infrastructure.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ReflectionUtil单元测试
 */
class ReflectionUtilTest {

    // 用于测试的示例类
    private static class TestClass {
        private String privateField = "privateValue"; // 用于测试getFieldValue方法
        public String publicField = "publicValue"; // 用于测试getFieldValue方法
        private int intField = 42; // 用于测试getIntegerFieldValue方法
        private boolean booleanField = true; // 用于测试getBooleanFieldValue方法
        private long longField = 1000L; // 用于测试getLongFieldValue方法
        
        private String privateMethod(String input) {
            return "private:" + input; // 用于测试invokeMethod方法
        }
        
        public String publicMethod(String input) {
            return "public:" + input; // 用于测试invokeMethod方法
        }
        
        private int sum(int a, int b) {
            return a + b; // 用于测试invokeMethod方法
        }
    }
    
    // 父类用于测试继承的方法调用
    private static class ParentTestClass {
        private String parentPrivateMethod() {
            return "parentPrivate"; // 用于测试从子类调用父类的私有方法
        }
    }
    
    private static class ChildTestClass extends ParentTestClass {
        // 子类不覆盖父类的方法
    }
    
    @Test
    void testGetFieldValue() {
        TestClass testObj = new TestClass();
        
        // 直接访问字段以避免未读取字段警告
        String privateValue = testObj.privateField;
        String publicValue = testObj.publicField;
        int intValue = testObj.intField;
        boolean boolValue = testObj.booleanField;
        long longValue = testObj.longField;
        
        // 测试私有字段
        assertEquals("privateValue", ReflectionUtil.getFieldValue(testObj, "privateField"));
        
        // 测试公共字段
        assertEquals("publicValue", ReflectionUtil.getFieldValue(testObj, "publicField"));
        
        // 测试不存在的字段
        assertNull(ReflectionUtil.getFieldValue(testObj, "nonExistentField"));
        
        // 测试null对象
        assertNull(ReflectionUtil.getFieldValue(null, "anyField"));
    }
    
    @Test
    void testSetFieldValue() {
        TestClass testObj = new TestClass();
        
        // 设置私有字段
        ReflectionUtil.setFieldValue(testObj, "privateField", "newValue");
        assertEquals("newValue", ReflectionUtil.getFieldValue(testObj, "privateField"));
        
        // 设置公共字段
        ReflectionUtil.setFieldValue(testObj, "publicField", "newPublicValue");
        assertEquals("newPublicValue", testObj.publicField);
    }
    
    @Test
    void testGetStringFieldValue() {
        TestClass testObj = new TestClass();
        
        // 测试字符串字段
        assertEquals("privateValue", ReflectionUtil.getStringFieldValue(testObj, "privateField"));
        
        // 测试不存在的字段返回空字符串
        assertEquals("", ReflectionUtil.getStringFieldValue(testObj, "nonExistentField"));
    }
    
    @Test
    void testGetBooleanFieldValue() {
        TestClass testObj = new TestClass();
        
        // 测试布尔字段
        assertTrue(ReflectionUtil.getBooleanFieldValue(testObj, "booleanField"));
        
        // 设置为false并测试
        ReflectionUtil.setFieldValue(testObj, "booleanField", false);
        assertFalse(ReflectionUtil.getBooleanFieldValue(testObj, "booleanField"));
        
        // 测试不存在的字段返回false
        assertFalse(ReflectionUtil.getBooleanFieldValue(testObj, "nonExistentField"));
    }
    
    @Test
    void testGetIntegerFieldValue() {
        TestClass testObj = new TestClass();
        
        // 测试整数字段
        assertEquals(42, ReflectionUtil.getIntegerFieldValue(testObj, "intField"));
        
        // 设置为新值并测试
        ReflectionUtil.setFieldValue(testObj, "intField", 100);
        assertEquals(100, ReflectionUtil.getIntegerFieldValue(testObj, "intField"));
        
        // 测试字符串转整数
        ReflectionUtil.setFieldValue(testObj, "privateField", "200");
        assertEquals(200, ReflectionUtil.getIntegerFieldValue(testObj, "privateField"));
        
        // 测试无效字符串返回0
        ReflectionUtil.setFieldValue(testObj, "privateField", "notANumber");
        assertEquals(0, ReflectionUtil.getIntegerFieldValue(testObj, "privateField"));
    }
    
    @Test
    void testGetLongFieldValue() {
        TestClass testObj = new TestClass();
        
        // 测试长整型字段
        assertEquals(1000L, ReflectionUtil.getLongFieldValue(testObj, "longField"));
        
        // 测试整数转长整型
        ReflectionUtil.setFieldValue(testObj, "intField", 2000);
        assertEquals(2000L, ReflectionUtil.getLongFieldValue(testObj, "intField"));
        
        // 测试字符串转长整型
        ReflectionUtil.setFieldValue(testObj, "privateField", "3000");
        assertEquals(3000L, ReflectionUtil.getLongFieldValue(testObj, "privateField"));
    }
    
    @Test
    void testInvokeMethod() throws Exception {
        TestClass testObj = new TestClass();
        
        // 测试调用私有方法
        String result1 = (String) ReflectionUtil.invokeMethod(
                testObj, 
                "privateMethod", 
                new Class[]{String.class}, 
                "testInput"
        );
        assertEquals("private:testInput", result1);
        
        // 测试调用公共方法
        String result2 = (String) ReflectionUtil.invokeMethod(
                testObj, 
                "publicMethod", 
                new Class[]{String.class}, 
                "testInput"
        );
        assertEquals("public:testInput", result2);
        
        // 测试带多个参数的方法
        int sumResult = (int) ReflectionUtil.invokeMethod(
                testObj, 
                "sum", 
                new Class[]{int.class, int.class}, 
                5, 10
        );
        assertEquals(15, sumResult);
        
        // 测试调用父类的私有方法
        ChildTestClass childObj = new ChildTestClass();
        String parentResult = (String) ReflectionUtil.invokeMethod(
                childObj, 
                "parentPrivateMethod", 
                new Class[]{}, 
                new Object[]{}
        );
        assertEquals("parentPrivate", parentResult);
        
        // 测试不存在的方法
        assertThrows(Exception.class, () -> {
            ReflectionUtil.invokeMethod(
                    testObj, 
                    "nonExistentMethod", 
                    new Class[]{}, 
                    new Object[]{}
            );
        });
    }
}