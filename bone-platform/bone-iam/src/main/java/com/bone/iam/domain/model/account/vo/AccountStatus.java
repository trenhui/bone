package com.bone.iam.domain.model.account.vo;

import com.bone.core.exception.DomainException;

public enum AccountStatus {
  DISABLED(0, "禁用"),
  ENABLED(1, "启用"),
  LOCKED(2, "锁定");

  private final int code;
  private final String description;

  AccountStatus(int code, String description) {
    this.code = code;
    this.description = description;
  }

  public int getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public static AccountStatus of(int code) {
    for (AccountStatus status : values()) {
      if (status.code == code) return status;
    }
    throw new DomainException("无效的状态码: " + code);
  }
}
