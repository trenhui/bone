package com.bone.engine.extension.core.router;

import com.bone.engine.extension.support.context.BizContext;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 缓存键工厂类
 * <p>
 * 统一管理所有缓存键的生成逻辑，避免在多个地方重复实现键生成功能
 * </p>
 * 
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class CacheKeyFactory {

    /**
     * 创建路由缓存键
     */
    public static CacheManager.RouteCacheKey createRouteCacheKey(Class<?> extPointType, Method method, BizContext<?> context) {
        return new CacheManager.RouteCacheKey(extPointType, method, context);
    }
    
    /**
     * 创建统计键
     */
    public static String createStatsKey(Class<?> extPointClass, Method method) {
        Objects.requireNonNull(extPointClass, "extPointClass cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
        return extPointClass.getSimpleName() + ":" + method.getName();
    }
    
    /**
     * 创建实现统计键
     */
    public static String createImplStatsKey(Class<?> extPointClass, Method method, Class<?> implementationType) {
        Objects.requireNonNull(extPointClass, "extPointClass cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(implementationType, "implementationType cannot be null");
        return extPointClass.getSimpleName() + ":" + method.getName() + ":" + implementationType.getSimpleName();
    }
    
    /**
     * 创建失败统计键
     */
    public static String createFailureKey(Class<?> extPointClass, Method method, Class<?> implementationType) {
        Objects.requireNonNull(extPointClass, "extPointClass cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(implementationType, "implementationType cannot be null");
        return "FAILURE:" + extPointClass.getSimpleName() + ":" + method.getName() + ":" + implementationType.getSimpleName();
    }
    
    /**
     * 创建表达式缓存键
     */
    public static String createExpressionCacheKey(String expressionString) {
        Objects.requireNonNull(expressionString, "expressionString cannot be null");
        return "EXPRESSION:" + expressionString;
    }
    
    /**
     * 创建扩展点缓存键
     */
    public static String createExtPointCacheKey(Class<?> extPointClass) {
        Objects.requireNonNull(extPointClass, "extPointClass cannot be null");
        return "EXTPOINT:" + extPointClass.getName();
    }
    
    /**
     * 创建扩展点缓存键（带上下文）
     */
    public static String createExtPointCacheKey(Class<?> extPointClass, BizContext<?> context) {
        Objects.requireNonNull(extPointClass, "extPointClass cannot be null");
        if (context == null) {
            return createExtPointCacheKey(extPointClass);
        }
        return "EXTPOINT:" + extPointClass.getName() + ":" + context.hashCode();
    }
}