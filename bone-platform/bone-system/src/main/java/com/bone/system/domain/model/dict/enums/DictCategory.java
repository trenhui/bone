package com.bone.system.domain.model.dict.enums;

import com.bone.core.exception.BizException;
import java.util.Arrays;

/**
 * 值域分类：决定「谁是真源」与「谁能写」。
 *
 * <ul>
 *   <li>{@link #ENUM}——真源是 Java 枚举，字典由代码同步而来，人工只润色文案、不新增语义。
 *   <li>{@link #LIST}——扁平值域，人工维护。
 *   <li>{@link #CASCADE}——多级树形值域（省市区、故障分类），人工维护，有父子关系。
 * </ul>
 *
 * <p>落库存 {@code name()}，与 DDL 的 {@code DEFAULT 'LIST'} 对齐。
 */
public enum DictCategory {
  /** 绑定 Java 枚举：可一键同步，可检查与代码的漂移。 */
  ENUM,
  /** 扁平列表：人工维护。 */
  LIST,
  /** 级联树：人工维护，支持父子层级。 */
  CASCADE;

  public static DictCategory of(String value) {
    if (value == null || value.isBlank()) {
      return LIST;
    }
    return Arrays.stream(values())
        .filter(c -> c.name().equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(
            () -> BizException.of("字典值域分类非法：" + value + "（可选 " + Arrays.toString(values()) + "）"));
  }

  public boolean isEnum() {
    return this == ENUM;
  }

  public boolean isCascade() {
    return this == CASCADE;
  }
}
