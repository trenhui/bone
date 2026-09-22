package com.bone.iam.domain.model.account.valueobject;

import com.bone.core.exception.DomainException;

public record Username(String value) {
  public Username {
    if (value == null || value.isBlank()) {
      throw new DomainException("用户名不能为空");
    }
    if (value.length() < 3 || value.length() > 50) {
      throw new DomainException("用户名长度必须在3-50之间");
    }
  }

  public static Username of(String value) {
    return new Username(value);
  }
}
