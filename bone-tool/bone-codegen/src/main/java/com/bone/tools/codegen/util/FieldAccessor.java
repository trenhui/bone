package com.bone.tools.codegen.util;

import java.lang.reflect.Field;
import java.util.Objects;
import cn.hutool.core.util.ReflectUtil;

/**
 * 通用字段访问器，用于安全地访问对象的字段
 * 避免直接依赖getter和setter方法，提高代码的兼容性和稳定性
 */
public class FieldAccessor {

    /**
     * 获取对象的字段值
     * @param object 目标对象
     * @param fieldName 字段名
     * @return 字段值，如果获取失败返回null
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object object, String fieldName) {
        if (object == null || fieldName == null) {
            return null;
        }
        
        try {
            Class<?> clazz = object.getClass();
            // 尝试直接获取字段
            Field field = getFieldRecursively(clazz, fieldName);
            if (field != null) {
                field.setAccessible(true);
                return (T) field.get(object);
            }
            
            // 如果直接获取字段失败，尝试通过getter方法获取
            // 这里简单处理，实际可以根据JavaBean命名规范构建getter方法名
            String getterMethodName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                java.lang.reflect.Method getter = clazz.getMethod(getterMethodName);
                return (T) getter.invoke(object);
            } catch (Exception e) {
                // getter方法不存在或调用失败，继续尝试
            }
        } catch (Exception e) {
            // 忽略所有异常
        }
        
        return null;
    }
    
    /**
     * 设置对象的字段值
     * @param object 目标对象
     * @param fieldName 字段名
     * @param value 要设置的值
     * @return 设置是否成功
     */
    public static boolean setFieldValue(Object object, String fieldName, Object value) {
        if (object == null || fieldName == null) {
            return false;
        }
        
        try {
            Class<?> clazz = object.getClass();
            // 尝试直接设置字段
            Field field = getFieldRecursively(clazz, fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(object, value);
                return true;
            }
            
            // 如果直接设置字段失败，尝试通过setter方法设置
            String setterMethodName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                Class<?> valueClass = value == null ? Object.class : value.getClass();
                java.lang.reflect.Method setter = findMethodRecursively(clazz, setterMethodName, valueClass);
                if (setter != null) {
                    setter.invoke(object, value);
                    return true;
                }
            } catch (Exception e) {
                // setter方法不存在或调用失败
            }
        } catch (Exception e) {
            // 忽略所有异常
        }
        
        return false;
    }
    
    /**
     * 递归获取类及其父类中的字段
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return 字段对象，如果不存在返回null
     */
    private static Field getFieldRecursively(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
    
    /**
     * 递归查找类及其父类中的方法
     * @param clazz 目标类
     * @param methodName 方法名
     * @param parameterTypes 参数类型列表
     * @return 方法对象，如果不存在返回null
     */
    private static java.lang.reflect.Method findMethodRecursively(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
    
    /**
     * 安全调用Mapper方法
     * @param mapper Mapper对象
     * @param methodName 方法名
     * @param args 方法参数
     * @return 方法执行结果，如果执行失败返回null
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeMapperMethod(Object mapper, String methodName, Object... args) {
        try {
            // 尝试直接调用，不指定参数类型
            return (T) ReflectUtil.invoke(mapper, methodName, args);
        } catch (Exception e) {
            // 忽略所有异常
        }
        return null;
    }
}