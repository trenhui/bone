//package com.bone.metadata.sdk.query.dsl.util;
//
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//
//import java.beans.Introspector;
//import java.lang.invoke.SerializedLambda;
//import java.lang.reflect.Method;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Function;
//
///**
// * Lambda表达式工具类 - 用于从Lambda表达式中提取字段名
// * 使用Caffeine缓存提高性能
// */
//public class LambdaUtils {
//
//    // 使用Caffeine缓存提高Lambda表达式解析性能
//    private static final Cache<String, String> FIELD_NAME_CACHE = Caffeine.newBuilder()
//            .expireAfterWrite(1, TimeUnit.HOURS)
//            .maximumSize(1000)
//            .build();
//
//    /**
//     * 从Lambda表达式中提取字段名
//     * @param fieldGetter Lambda表达式
//     * @param <T> 实体类型
//     * @param <R> 字段类型
//     * @return 字段名
//     */
//    public static <T, R> String extractFieldName(Function<T, R> fieldGetter) {
//        try {
//            // 生成缓存键
//            String cacheKey = generateCacheKey(fieldGetter);
//
//            // 从缓存获取
//            String fieldName = FIELD_NAME_CACHE.getIfPresent(cacheKey);
//            if (fieldName != null) {
//                return fieldName;
//            }
//
//            // 获取SerializedLambda
//            Method method = fieldGetter.getClass().getDeclaredMethod("writeReplace");
//            method.setAccessible(true);
//            SerializedLambda serializedLambda = (SerializedLambda) method.invoke(fieldGetter);
//
//            // 提取方法名
//            String methodName = serializedLambda.getImplMethodName();
//
//            // 从方法名解析字段名
//            fieldName = resolveFieldName(methodName);
//
//            // 缓存结果
//            FIELD_NAME_CACHE.put(cacheKey, fieldName);
//
//            return fieldName;
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to extract field name from lambda expression", e);
//        }
//    }
//
//    /**
//     * 从方法名解析字段名
//     * @param methodName getter/setter方法名
//     * @return 字段名
//     */
//    private static String resolveFieldName(String methodName) {
//        if (methodName.startsWith("get")) {
//            return Introspector.decapitalize(methodName.substring(3));
//        } else if (methodName.startsWith("is")) {
//            return Introspector.decapitalize(methodName.substring(2));
//        }
//        throw new IllegalArgumentException("Invalid method name for field extraction: " + methodName);
//    }
//
//    /**
//     * 生成缓存键
//     */
//    private static <T, R> String generateCacheKey(Function<T, R> fieldGetter) {
//        return fieldGetter.getClass().getName() + "_" + fieldGetter.hashCode();
//    }
//}