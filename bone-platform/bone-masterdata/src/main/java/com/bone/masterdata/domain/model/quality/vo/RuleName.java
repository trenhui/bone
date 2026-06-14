package com.bone.masterdata.domain.model.quality.vo;

import com.bone.core.exception.DomainException;

public record RuleName(String value) {
  public RuleName {
    if (value == null || value.isBlank()) {
      throw new DomainException("规则名称不能为空");
    }
    if (value.length() < 2 || value.length() > 50) {
      throw new DomainException("规则名称长度必须在2-50字符之间");
    }
  }

  public static RuleName of(String value) {
    return new RuleName(value);
  }
}
