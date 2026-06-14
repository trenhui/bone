package com.bone.masterdata.domain.model.quality.vo;

import com.bone.core.exception.DomainException;

public record QualityReportId(Long value) {
  public QualityReportId {
    if (value == null || value <= 0) {
      throw new DomainException("质量报告ID不能为空且必须大于0");
    }
  }

  public static QualityReportId of(Long value) {
    return new QualityReportId(value);
  }
}
