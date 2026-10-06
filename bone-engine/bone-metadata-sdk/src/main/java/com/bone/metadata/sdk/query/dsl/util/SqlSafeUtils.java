package com.bone.metadata.sdk.query.dsl.util;

import java.util.regex.Pattern;

/**
 * SQL 安全工具类。
 *
 * <p><b>与 {@code SqlInjectionPreventer} 是两份独立实现，且字段名规则不一致</b>（2026-10-05 实测）： 本类 {@code
 * FIELD_NAME_PATTERN} 为 {@code ^[a-zA-Z_][a-zA-Z0-9_]*$}（<b>不含点号</b>）， 而 {@code
 * SqlInjectionPreventer.SQL_FIELD_PATTERN} 为 {@code ^[a-zA-Z_][a-zA-Z0-9_.]*$}（<b>含点号</b>）。 后果是同一个
 * {@code a.b}：走 {@code SqlBuilder}/{@code DefaultFluentQuery} 会被<b>抛异常</b>， 走 {@code
 * AggregationBuilder.groupBy} 却<b>能通过</b> —— 同一平台对「合法字段名」的判断不一致。
 *
 * <p><b>为什么不统一</b>：任一方向都是 SDK 行为变更（放开点号 = 放宽既有校验；收紧 = 让现存 {@code groupBy("a.b")} 的调用方开始报错），且 {@code
 * bone-metadata-sdk} 是<b>对外 SDK</b>。 故此处只把差异写明并用测试锁定现状，统一的时机与方向需架构决策。
 *
 * <p><b>为什么标注 Deprecated 而不是删除</b>：本类除 {@link #isValidFieldName} 外，其余 public 方法在
 * 本仓<b>零生产调用</b>，但它是<b>对外 SDK 的 public API</b>，直接删除是 breaking change。 故标 {@code @Deprecated} 指向
 * {@code SqlInjectionPreventer} 的对应能力，给后来者指路并留出迁移窗口。
 */
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
  @Deprecated // 2026-10-05：本方法在本仓零生产调用，且与 SqlInjectionPreventer 重复；标废弃而非删除（对外 SDK，删除是 breaking
  // change）
  @SuppressWarnings("deprecation")
  public static boolean isValidTableAlias(String alias) {
    return alias != null && TABLE_ALIAS_PATTERN.matcher(alias).matches();
  }

  /**
   * 清理和验证字段名，如果不安全则抛出异常
   *
   * @param fieldName 字段名
   * @return 清理后的字段名
   */
  @Deprecated // 2026-10-05：本方法在本仓零生产调用，且与 SqlInjectionPreventer 重复；标废弃而非删除（对外 SDK，删除是 breaking
  // change）
  @SuppressWarnings("deprecation")
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
  @Deprecated // 2026-10-05：本方法在本仓零生产调用，且与 SqlInjectionPreventer 重复；标废弃而非删除（对外 SDK，删除是 breaking
  // change）
  @SuppressWarnings("deprecation")
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
  @Deprecated // 2026-10-05：本方法在本仓零生产调用，且与 SqlInjectionPreventer 重复；标废弃而非删除（对外 SDK，删除是 breaking
  // change）
  @SuppressWarnings("deprecation")
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
  @Deprecated // 2026-10-05：本方法在本仓零生产调用，且与 SqlInjectionPreventer 重复；标废弃而非删除（对外 SDK，删除是 breaking
  // change）
  @SuppressWarnings("deprecation")
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
