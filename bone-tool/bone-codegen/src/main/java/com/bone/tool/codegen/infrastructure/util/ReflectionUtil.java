package com.bone.tool.codegen.infrastructure.util;

import java.lang.reflect.Field;

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
            Field field = getDeclaredField(object.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(object);
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
            Field field = getDeclaredField(object.getClass(), fieldName);
            field.setAccessible(true);
            field.set(object, value);
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
    
    /**
     * 递归获取字段（包括父类）
     */
    private static Field getDeclaredField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                field = getDeclaredField(superClass, fieldName);
            } else {
                throw e;
            }
        }
        return field;
    }
    
    /**
     * 通过反射调用对象的方法
     * @param object 目标对象
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @param params 参数值数组
     * @return 方法调用的返回值
     * @throws Exception 调用失败时抛出异常
     */
    public static Object invokeMethod(Object object, String methodName, Class<?>[] paramTypes, Object... params) throws Exception {
        try {
            // 尝试直接在当前类中查找方法
            java.lang.reflect.Method method = object.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(object, params);
        } catch (NoSuchMethodException e) {
            // 如果在当前类中找不到，尝试在父类中查找
            Class<?> superClass = object.getClass().getSuperclass();
            if (superClass != null && superClass != Object.class) {
                java.lang.reflect.Method method = superClass.getDeclaredMethod(methodName, paramTypes);
                method.setAccessible(true);
                return method.invoke(object, params);
            }
            throw e;
        }
    }
}