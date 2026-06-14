// package com.bone.metadata.sdk.query.dsl.util;
//
// import java.beans.Introspector;
// import java.lang.invoke.SerializedLambda;
// import java.lang.reflect.Method;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.ConcurrentMap;
// import java.util.function.Function;
//
/// **
// * Lambda表达式字段名提取工具
// * 基于业界最佳实践，提供类型安全的字段名提取
// */
// public class FieldExtractor {
//
//    private static final ConcurrentMap<String, String> FIELD_NAME_CACHE = new
// ConcurrentHashMap<>();
//
//    private FieldExtractor() {
//        // 工具类，防止实例化
//    }
//
//    /**
//     * 从Lambda表达式提取字段名
//     */
//    public static <T, R> String extractFieldName(Function<T, R> function) {
//        // 检查是否为序列化Lambda
//        if (!function.getClass().isSynthetic()) {
//            throw new IllegalArgumentException("Expected a lambda expression, but got: " +
// function.getClass());
//        }
//
//        String cacheKey = function.getClass().getName() + "@" + System.identityHashCode(function);
//
//        return FIELD_NAME_CACHE.computeIfAbsent(cacheKey, key -> {
//            try {
//                Method method = function.getClass().getDeclaredMethod("writeReplace");
//                method.setAccessible(true);
//                SerializedLambda serializedLambda = (SerializedLambda) method.invoke(function);
//
//                String getterMethod = serializedLambda.getImplMethodName();
//
//                // 处理boolean类型的is前缀
//                if (getterMethod.startsWith("is")) {
//                    return Introspector.decapitalize(getterMethod.substring(2));
//                }
//                // 处理get前缀
//                else if (getterMethod.startsWith("get")) {
//                    return Introspector.decapitalize(getterMethod.substring(3));
//                }
//                // 如果没有前缀，直接返回方法名
//                else {
//                    return getterMethod;
//                }
//            } catch (Exception e) {
//                throw new IllegalArgumentException("无法从Lambda表达式提取字段名: " + e.getMessage(), e);
//            }
//        });
//    }
// }
