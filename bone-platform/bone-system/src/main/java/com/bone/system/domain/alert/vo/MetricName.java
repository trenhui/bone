package com.bone.system.domain.alert.vo;

import com.bone.core.exception.DomainException;

public record MetricName(String value) {
  public MetricName {
    if (value == null || value.isBlank()) {
      throw new DomainException("指标名称不能为空");
    }
    if (value.length() > 255) {
      throw new DomainException("指标名称长度不能超过255字符");
    }
  }

  public static MetricName of(String value) {
    return new MetricName(value);
  }
}
