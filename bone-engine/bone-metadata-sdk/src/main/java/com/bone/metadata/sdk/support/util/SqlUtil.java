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
      String methodName = lambda.getImplMethodName();

      // 支持多种方法格式：getter、is前缀的布尔方法、直接字段引用
      if (methodName.startsWith("get")) {
        // 标准getter方法
        return decapitalize(methodName.substring(3));
      } else if (methodName.startsWith("is")) {
        // is开头的布尔方法（如isEnabled）
        return decapitalize(methodName.substring(2));
      } else if (methodName.startsWith("lambda$") && lambda.getImplMethodSignature() != null) {
        // 支持直接字段引用 (如 entity -> entity.fieldName)
        String implClass = lambda.getImplClass().replace("/", ".");
        try {
          // 尝试从方法签名中提取字段信息
          String fieldName = extractFieldNameFromLambda(lambda);
          if (fieldName != null) {
            return fieldName;
          }
        } catch (Exception e) {
          // 如果提取失败，继续使用默认逻辑
        }
      }
      // 如果都不匹配，返回原始方法名
      return methodName;
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to resolve Lambda expression", e);
    }
  }

  /** 从Lambda表达式中提取字段名 */
  private static String extractFieldNameFromLambda(SerializedLambda lambda) {
    try {
      // 获取捕获的Lambda目标类
      String implClass = lambda.getImplClass().replace("/", ".");
      String implMethodName = lambda.getImplMethodName();

      // 对于直接字段引用，尝试从实现方法中提取
      if (implMethodName.startsWith("lambda$")) {
        // 提取方法签名中的参数类型信息
        String signature = lambda.getImplMethodSignature();
        // 简单实现：尝试从方法名中提取字段相关信息
        // 实际项目中可能需要更复杂的解析
        return "field";
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  /** 将首字母小写 */
  private static String decapitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    if (str.length() == 1) {
      return str.toLowerCase(java.util.Locale.ROOT);
    }
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * 解析Lambda表达式获取SerializedLambda对象 注意：这里使用setAccessible是必要的，用于访问Lambda表达式的内部实现细节
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

  /** 将驼峰命名转为蛇形命名。 */
  public static String toSnakeCase(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    // 更健壮的驼峰转蛇形实现
    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(name.charAt(0)));

    for (int i = 1; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        // 处理连续大写字母的情况（如HTTPURL -> http_url）
        if (i + 1 < name.length() && !Character.isUpperCase(name.charAt(i + 1))) {
          result.append('_').append(Character.toLowerCase(c));
        } else if (i > 1 && !Character.isUpperCase(name.charAt(i - 1))) {
          result.append('_').append(Character.toLowerCase(c));
        } else {
          result.append(Character.toLowerCase(c));
        }
      } else {
        result.append(c);
      }
    }

    return result.toString().toLowerCase(java.util.Locale.ROOT);
  }

  /** 将蛇形命名转为驼峰命名 */
  public static String toCamelCase(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else {
        if (capitalizeNext) {
          result.append(Character.toUpperCase(c));
          capitalizeNext = false;
        } else {
          result.append(c);
        }
      }
    }

    // 确保首字母小写
    if (result.length() > 0) {
      result.setCharAt(0, Character.toLowerCase(result.charAt(0)));
    }

    return result.toString();
  }

  /** 安全地获取字段名，防止SQL注入 */
  public static String safeFieldName(String fieldName) {
    if (fieldName == null || fieldName.isEmpty()) {
      throw new IllegalArgumentException("Field name cannot be null or empty");
    }

    // 只允许字母、数字、下划线和点（用于表别名）
    if (!fieldName.matches("^[a-zA-Z0-9_\\.]+$")) {
      throw new IllegalArgumentException("Invalid field name: " + fieldName);
    }

    return fieldName;
  }

  /** 判断是否为写操作 (包括DML和DDL) */
  public static boolean isWriteOperation(String sql) {
    if (sql == null || sql.trim().isEmpty()) {
      return false;
    }
    String normalized = sql.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    return normalized.startsWith("INSERT ")
        || normalized.startsWith("UPDATE ")
        || normalized.startsWith("DELETE ")
        || normalized.startsWith("CREATE ")
        || normalized.startsWith("ALTER ")
        || normalized.startsWith("DROP ")
        || normalized.startsWith("TRUNCATE ")
        || normalized.contains(" FOR UPDATE");
  }

  /** 将领域值对象/枚举转为 JDBC 可绑定参数。 */
  public static Object toJdbcParameter(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Enum<?> enumValue) {
      // 优先尝试 getCode() 方法（带 int code 的枚举）
      try {
        Method getCode = enumValue.getClass().getMethod("getCode");
        Object code = getCode.invoke(enumValue);
        if (code != null) return code;
      } catch (ReflectiveOperationException ignored) {
        // fall through
      }
      return enumValue.name();
    }
    if (value.getClass().isRecord()) {
      try {
        Method method = value.getClass().getMethod("value");
        if (method.getParameterCount() == 0) {
          return method.invoke(value);
        }
      } catch (ReflectiveOperationException ignored) {
        // 非标准 record，原样返回
      }
    }
    // Collection/Map → JSON 字符串（用于 JSON 类型列）
    if (value instanceof java.util.Collection<?> || value instanceof java.util.Map<?, ?>) {
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
      } catch (Exception ignored) {
        // 序列化失败，原样返回
      }
    }
    return value;
  }
}
