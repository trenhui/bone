package com.bone.metadata.sdk.support.util;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;

public class SqlUtil {

    public static <T, R> String extractFieldName(Function<T, R> keyExtractor) {
        return getFieldName(keyExtractor);
    }


    public static <T, R> String getFieldName(Function<T, R> keyExtractor) {
        try {
            // 获取 SerializedLambda
            SerializedLambda lambda = resolve(keyExtractor);
            String methodName = lambda.getImplMethodName();  //

            // 如果是 getter 方法，去掉 'get' 前缀
            if (methodName.startsWith("get")) {
                return methodName.substring(3);  // 去掉 'get' 前缀
            } else {
                throw new IllegalArgumentException("Lambda method name is not in the expected format.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to resolve Lambda expression", e);
        }
    }

    public static <T> SerializedLambda resolve(Function<T, ?> keyExtractor) {
        try {
            // 通过反射获取 'writeReplace' 方法
            Method writeReplaceMethod = keyExtractor.getClass().getDeclaredMethod("writeReplace");
            writeReplaceMethod.setAccessible(true);
            return (SerializedLambda) writeReplaceMethod.invoke(keyExtractor);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to resolve Lambda expression", e);
        }
    }

    /**
     * 将驼峰命名转为蛇形命名。
     */
    public static String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        if (input.length() == 1) {
            return input.toLowerCase();
        }
        return Character.toLowerCase(input.charAt(0)) + input.substring(1);
    }

    /**
     * 判断是否为写操作 (包括DML和DDL)
     */
    public static boolean isWriteOperation(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        String normalized = sql.trim().toUpperCase().replaceAll("\\s+", " ");
        return normalized.startsWith("INSERT ") ||
                normalized.startsWith("UPDATE ") ||
                normalized.startsWith("DELETE ") ||
                normalized.startsWith("CREATE ") ||
                normalized.startsWith("ALTER ") ||
                normalized.startsWith("DROP ") ||
                normalized.startsWith("TRUNCATE ") ||
                normalized.contains(" FOR UPDATE");
    }
}
