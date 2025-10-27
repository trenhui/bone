package com.bone.engine.extension.util;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 反射工具类
 * <p>
 * 提供安全的反射调用方法，避免反射操作中的异常导致业务逻辑中断
 * 包含通用的属性获取、方法调用等安全封装
 */
public class ReflectionUtils {
    
    /**
     * 私有构造函数，防止实例化
     */
    private ReflectionUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * 安全获取字符串属性
     * 
     * @param obj 目标对象
     * @param methodName 方法名
     * @return 字符串属性值，如果调用失败则返回null
     */
    public static String safeGetString(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            // 忽略方法调用失败
            return null;
        }
    }
    
    /**
     * 安全获取标签映射
     * 
     * @param obj 目标对象
     * @param methodName 方法名
     * @return 标签映射，如果调用失败则返回null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> safeGetTags(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return (Map<String, Object>) result;
        } catch (Exception e) {
            // 忽略方法调用失败
            return null;
        }
    }
    
    /**
     * 安全调用无参方法
     * 
     * @param obj 目标对象
     * @param methodName 方法名
     * @return 方法返回值，如果调用失败则返回null
     */
    public static Object safeInvoke(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            // 忽略方法调用失败
            return null;
        }
    }
    
    /**
     * 安全调用带参数的方法
     * 
     * @param obj 目标对象
     * @param methodName 方法名
     * @param paramTypes 参数类型
     * @param args 参数值
     * @return 方法返回值，如果调用失败则返回null
     */
    public static Object safeInvoke(Object obj, String methodName, Class<?>[] paramTypes, Object[] args) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = obj.getClass().getMethod(methodName, paramTypes);
            return method.invoke(obj, args);
        } catch (Exception e) {
            // 忽略方法调用失败
            return null;
        }
    }
    
    /**
     * 安全获取类的字符串名称
     * 
     * @param clazz 目标类
     * @return 类的简单名称，如果为null则返回"null"
     */
    public static String safeGetClassName(Class<?> clazz) {
        return clazz != null ? clazz.getSimpleName() : "null";
    }
    
    /**
     * 安全获取类的完整名称
     * 
     * @param clazz 目标类
     * @return 类的完整名称，如果为null则返回"null"
     */
    public static String safeGetFullClassName(Class<?> clazz) {
        return clazz != null ? clazz.getName() : "null";
    }
}