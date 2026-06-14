package com.bone.masterdata.domain.model.quality.vo;

import com.bone.core.exception.DomainException;

public record QualityCheckId(Long value) {
  public QualityCheckId {
    if (value == null || value <= 0) {
      throw new DomainException("质量检查ID不能为空且必须大于0");
    }
  }

  public static QualityCheckId of(Long value) {
    return new QualityCheckId(value);
  }
}
