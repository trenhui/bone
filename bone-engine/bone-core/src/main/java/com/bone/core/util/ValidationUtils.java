package com.bone.core.util;

import java.util.Collection;

/**
 * 验证工具类
 * 提供统一的验证方法，避免代码重复
 */
public final class ValidationUtils {

    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 验证字符串不为空
     * @param value 待验证的值
     * @param message 错误消息
     * @throws IllegalArgumentException 当值为空时抛出
     */
    public static void validateNotEmpty(String value, String message) {
        if (ObjectUtils.isEmpty(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 验证对象不为空
     * @param object 待验证的对象
     * @param message 错误消息
     * @throws IllegalArgumentException 当对象为空时抛出
     */
    public static void validateNotNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * 验证表达式为真
     * @param expression 布尔表达式
     * @param message 错误消息
     * @throws IllegalArgumentException 当表达式为假时抛出
     */
    public static void validateTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * 验证集合不为空
     * @param collection 待验证的集合
     * @param message 错误消息
     * @throws IllegalArgumentException 当集合为空时抛出
     */
    public static void validateNotEmpty(Iterable<?> collection, String message) {
        if (collection == null || !collection.iterator().hasNext()) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * 验证Map不为空
     * @param map 待验证的Map
     * @param message 错误消息
     * @throws IllegalArgumentException 当Map为空时抛出
     */
    public static void validateNotEmpty(java.util.Map<?, ?> map, String message) {
        if (MapUtils.isEmpty(map)) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * 验证条件为假
     * @param condition 待验证的条件
     * @param message 错误消息
     * @throws IllegalArgumentException 当条件为真时抛出
     */
    public static void validateFalse(boolean condition, String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * 验证参数不为空
     * @param parameter 参数名称
     * @param value 参数值
     * @throws IllegalArgumentException 当参数值为空时抛出
     */
    public static void validateParameterNotEmpty(String parameter, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter '" + parameter + "' cannot be null");
        }
        if (value instanceof String && ObjectUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Parameter '" + parameter + "' cannot be empty");
        }
        if (value instanceof Collection<?> && CollectionUtils.isEmpty((Collection<?>) value)) {
            throw new IllegalArgumentException("Parameter '" + parameter + "' cannot be empty");
        }
    }
}