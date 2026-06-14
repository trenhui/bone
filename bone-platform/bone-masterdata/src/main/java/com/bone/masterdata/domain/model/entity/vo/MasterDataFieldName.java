package com.bone.masterdata.domain.model.entity.vo;

import com.bone.core.exception.DomainException;

public record MasterDataFieldName(String value) {
  public MasterDataFieldName {
    if (value == null || value.isBlank()) {
      throw new DomainException("主数字段名称不能为空");
    }
    if (value.length() < 1 || value.length() > 30) {
      throw new DomainException("主数字段名称长度必须在1-30字符之间");
    }
  }

  public static MasterDataFieldName of(String value) {
    return new MasterDataFieldName(value);
  }
}
