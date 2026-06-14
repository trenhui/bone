package com.bone.metadata.sdk.query.dsl.util;

import java.util.regex.Pattern;

/** SQL安全工具类 - 提供SQL注入防护和SQL安全相关的工具方法 */
public class SqlSafeUtils {

  // 字段名验证正则表达式
  private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

  // 表别名验证正则表达式
  private static final Pattern TABLE_ALIAS_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

  /**
   * 验证字段名是否安全
   *
   * @param fieldName 字段名
   * @return 是否安全
   */
  public static boolean isValidFieldName(String fieldName) {
    return fieldName != null && FIELD_NAME_PATTERN.matcher(fieldName).matches();
  }

  /**
   * 验证表别名是否安全
   *
   * @param alias 表别名
   * @return 是否安全
   */
  public static boolean isValidTableAlias(String alias) {
    return alias != null && TABLE_ALIAS_PATTERN.matcher(alias).matches();
  }

  /**
   * 清理和验证字段名，如果不安全则抛出异常
   *
   * @param fieldName 字段名
   * @return 清理后的字段名
   */
  public static String sanitizeFieldName(String fieldName) {
    if (!isValidFieldName(fieldName)) {
      throw new IllegalArgumentException("Invalid field name: " + fieldName);
    }
    return fieldName;
  }

  /**
   * 清理和验证表别名，如果不安全则抛出异常
   *
   * @param alias 表别名
   * @return 清理后的表别名
   */
  public static String sanitizeTableAlias(String alias) {
    if (!isValidTableAlias(alias)) {
      throw new IllegalArgumentException("Invalid table alias: " + alias);
    }
    return alias;
  }

  /**
   * 检查是否包含SQL注入风险字符
   *
   * @param value 要检查的值
   * @return 是否包含风险字符
   */
  public static boolean containsSqlInjectionRisk(String value) {
    if (value == null) {
      return false;
    }

    // 简单的SQL注入检测规则
    String lowerValue = value.toLowerCase();
    String[] keywords = {
      "union",
      "select",
      "insert",
      "update",
      "delete",
      "drop",
      "truncate",
      "alter",
      "create",
      "exec",
      "execute",
      "xp_",
      "sp_",
      "--",
      "/*",
      "*/",
      "'",
      "\"",
      ";",
      "xp_cmdshell"
    };

    for (String keyword : keywords) {
      if (lowerValue.contains(keyword)) {
        return true;
      }
    }

    return false;
  }

  /**
   * 转义SQL特殊字符
   *
   * @param value 要转义的值
   * @return 转义后的值
   */
  public static String escapeSql(String value) {
    if (value == null) {
      return null;
    }
    return value
        .replace("'", "''")
        .replace("\"", "\\\"")
        .replace("\\", "\\\\")
        .replace("\0", "\\0")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
