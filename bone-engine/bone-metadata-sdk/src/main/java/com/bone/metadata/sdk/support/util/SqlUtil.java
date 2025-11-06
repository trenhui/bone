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

    /**
     * 解析Lambda表达式获取SerializedLambda对象
     * 注意：这里使用setAccessible是必要的，用于访问Lambda表达式的内部实现细节
     * 在当前SDK上下文中，这是安全的，因为我们只访问用户传递的Lambda函数
     */
    public static <T> SerializedLambda resolve(Function<T, ?> keyExtractor) {
        try {
            // 通过反射获取 'writeReplace' 方法
            Method writeReplaceMethod = keyExtractor.getClass().getDeclaredMethod("writeReplace");
            // 设置方法可访问，这在Lambda表达式处理中是必要的
            writeReplaceMethod.setAccessible(true);
            return (SerializedLambda) writeReplaceMethod.invoke(keyExtractor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to resolve Lambda expression", e);
        }
    }

    /**
     * 将驼峰命名转为蛇形命名。
     */
    public static String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    public static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        if (input.length() == 1) {
            return input.toLowerCase(java.util.Locale.ROOT);
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
        String normalized = sql.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
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
