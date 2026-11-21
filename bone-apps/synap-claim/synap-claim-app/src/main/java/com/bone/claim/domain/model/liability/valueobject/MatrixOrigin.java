package com.bone.claim.domain.model.liability.valueobject;

/**
 * 责任覆盖来源三态（受控码，因子矩阵与覆盖三态共用）。
 *
 * <ul>
 *   <li>{@code INHERIT}：通用·继承标准，标准升级可覆盖；
 *   <li>{@code EXCLUSIVE}：专属·覆盖标准，本主体独立写入；
 *   <li>{@code MODIFIED}：专属·改，曾人工调整，标准升级不再覆盖。
 * </ul>
 */
public enum MatrixOrigin {
  INHERIT,
  EXCLUSIVE,
  MODIFIED;

  /** DB name → 枚举（SDK String 持久化口径）；空值按专属兜底，与 DDL DEFAULT 'EXCLUSIVE' 对齐。 */
  public static MatrixOrigin ofName(String name) {
    if (name == null || name.isBlank()) {
      return EXCLUSIVE;
    }
    for (MatrixOrigin v : values()) {
      if (v.name().equalsIgnoreCase(name.trim())) {
        return v;
      }
    }
    throw new IllegalArgumentException("未知MatrixOrigin: " + name);
  }
}
