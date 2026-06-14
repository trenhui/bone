package com.bone.core.enums;

import java.util.Arrays;
import lombok.Getter;

/** SQL 操作符枚举类，用于表示常见的 SQL 查询操作符。 */
@Getter
public enum Operator {
  // 基础比较运算符
  EQ("=", "等于"),
  NE("<>", "不等于"),
  GT(">", "大于"),
  LT("<", "小于"),
  GTE(">=", "大于等于"),
  LTE("<=", "小于等于"),

  // 集合操作
  IN("IN", "范围查询"),
  NOT_IN("NOT IN", "不在范围内"),

  // 空值判断
  IS_NULL("IS NULL", "为空"),
  IS_NOT_NULL("IS NOT NULL", "不为空"),

  // 字符串匹配
  LIKE("LIKE", "全模糊查询"),
  LIKE_LEFT("LIKE", "左模糊查询"),
  LIKE_RIGHT("LIKE", "右模糊查询"),
  NOT_LIKE("NOT LIKE", "非前匹配"),
  FULL_LIKE("LIKE", "全模糊查询"), // 使用相同SQL符号但不同语义

  // 范围查询
  BETWEEN("BETWEEN", "区间查询"),
  AUTO("=", "自动");

  private final String symbol;
  private final String description;

  Operator(String symbol, String description) {
    this.symbol = symbol;
    this.description = description;
  }

  /**
   * 根据操作符符号获取对应的枚举类型。
   *
   * @param symbol 操作符符号
   * @return 对应的 Operator 枚举值
   */
  public static Operator getBySymbol(String symbol) {
    return Arrays.stream(values())
        .filter(op -> op.getSymbol().equals(symbol))
        .findFirst()
        .orElse(null);
  }

  /**
   * 根据操作符的代码获取枚举值（支持大小写不敏感）。
   *
   * @param code 枚举代码
   * @return 对应的 Operator 枚举值
   */
  public static Operator fromCode(String code) {
    return Arrays.stream(values())
        .filter(op -> op.name().equalsIgnoreCase(code))
        .findFirst()
        .orElse(null);
  }
}
