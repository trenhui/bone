package com.bone.metadata.sdk.test.common;

/**
 * 简化的断言工具类
 * 替代JUnit的断言功能，提供基本的断言方法
 */
public class SimpleAssertions {
    /**
     * 断言两个对象相等
     */
    public static void assertEquals(Object expected, Object actual, String message) {
        boolean isEqual = (expected == null && actual == null) || 
                          (expected != null && expected.equals(actual));
        if (!isEqual) {
            throw new AssertionError(message + " [期望值: " + expected + ", 实际值: " + actual + "]");
        }
        System.out.println("断言通过: " + message);
    }
    
    /**
     * 断言条件为真
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        System.out.println("断言通过: " + message);
    }
    
    /**
     * 断言条件为假
     */
    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
        System.out.println("断言通过: " + message);
    }
    
    /**
     * 断言对象为null
     */
    public static void assertNull(Object object, String message) {
        if (object != null) {
            throw new AssertionError(message + " [对象不为null: " + object + "]");
        }
        System.out.println("断言通过: " + message);
    }
    
    /**
     * 断言对象不为null
     */
    public static void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new AssertionError(message);
        }
        System.out.println("断言通过: " + message);
    }
    
    /**
     * 断言数组长度
     */
    public static void assertArrayLength(Object[] array, int expectedLength, String message) {
        int actualLength = array != null ? array.length : 0;
        assertEquals(expectedLength, actualLength, message + " (数组长度不匹配)");
    }
    
    /**
     * 断言集合大小
     */
    public static void assertCollectionSize(java.util.Collection<?> collection, int expectedSize, String message) {
        int actualSize = collection != null ? collection.size() : 0;
        assertEquals(expectedSize, actualSize, message + " (集合大小不匹配)");
    }
    
    /**
     * 断言执行方法会抛出指定异常
     */
    public static <T extends Throwable> void assertThrows(Class<T> expectedThrowable, Executable executable, String message) {
        try {
            executable.execute();
            fail(message + " (未抛出预期异常: " + expectedThrowable.getName() + ")");
        } catch (Throwable actualThrown) {
            if (!expectedThrowable.isInstance(actualThrown)) {
                String errorMsg = message + " (抛出异常类型不匹配: 预期 " + 
                                  expectedThrowable.getName() + ", 实际 " + 
                                  actualThrown.getClass().getName() + ")";
                throw new AssertionError(errorMsg, actualThrown);
            }
            System.out.println("断言通过: " + message);
        }
    }
    
    /**
     * 使当前测试失败
     */
    public static void fail(String message) {
        throw new AssertionError(message);
    }
    
    /**
     * 函数式接口，用于执行可能抛出异常的方法
     */
    public interface Executable {
        void execute() throws Throwable;
    }
}