package com.bone.tool.codegen.infrastructure.util;

import cn.hutool.core.util.ReflectUtil;

/**
 * 反射工具类，用于统一处理对象字段的访问
 */
public class ReflectionUtil {

    /**
     * 获取对象的字段值
     * @param object 对象实例
     * @param fieldName 字段名
     * @return 字段值
     */
    public static Object getFieldValue(Object object, String fieldName) {
        try {
            return ReflectUtil.getFieldValue(object, fieldName);
        } catch (Exception e) {
            // 忽略异常
            return null;
        }
    }

    /**
     * 设置对象的字段值
     * @param object 对象实例
     * @param fieldName 字段名
     * @param value 字段值
     */
    public static void setFieldValue(Object object, String fieldName, Object value) {
        try {
            ReflectUtil.setFieldValue(object, fieldName, value);
        } catch (Exception e) {
            // 忽略异常
        }
    }

    /**
     * 获取字符串类型的字段值
     * @param object 对象实例
     * @param fieldName 字段名
     * @return 字符串类型的字段值，如果为null则返回空字符串
     */
    public static String getStringFieldValue(Object object, String fieldName) {
        Object value = getFieldValue(object, fieldName);
        return value != null ? String.valueOf(value) : "";
    }

    /**
     * 获取布尔类型的字段值
     * @param object 对象实例
     * @param fieldName 字段名
     * @return 布尔类型的字段值，如果为null则返回false
     */
    public static Boolean getBooleanFieldValue(Object object, String fieldName) {
        Object value = getFieldValue(object, fieldName);
        return value instanceof Boolean ? (Boolean) value : Boolean.FALSE;
    }

    /**
     * 获取整数类型的字段值
     * @param object 对象实例
     * @param fieldName 字段名
     * @return 整数类型的字段值，如果为null则返回0
     */
    public static Integer getIntegerFieldValue(Object object, String fieldName) {
        Object value = getFieldValue(object, fieldName);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 获取长整型的字段值
     * @param object 对象实例
     * @param fieldName 字段名
     * @return 长整型的字段值，如果为null则返回0L
     */
    public static Long getLongFieldValue(Object object, String fieldName) {
        Object value = getFieldValue(object, fieldName);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
}