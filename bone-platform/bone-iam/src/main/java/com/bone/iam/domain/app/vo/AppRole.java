package com.bone.iam.domain.app.vo;

import com.bone.core.exception.DomainException;

/** 用户在应用内的角色：admin / developer / viewer。 */
public enum AppRole {
  ADMIN,
  DEVELOPER,
  VIEWER;

  /** 对外（API）小写表示，匹配前端 {@code 'admin' | 'developer' | 'viewer'} 联合类型。 */
  public String externalName() {
    return name().toLowerCase();
  }

  /** 由对外小写表示解析为领域枚举，入参非法时抛 {@link DomainException}。 */
  public static AppRole fromExternal(String role) {
    if (role == null || role.isBlank()) {
      throw new DomainException("应用角色不能为空");
    }
    try {
      return AppRole.valueOf(role.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new DomainException("无效的应用角色: " + role);
    }
  }
}
