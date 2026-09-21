package com.bone.masterdata.domain.model.entity.vo;

import com.bone.core.exception.DomainException;

public record MasterDataEntityCode(String value) {
  public MasterDataEntityCode {
    if (value == null || value.isBlank()) {
      throw new DomainException("主数据实体编码不能为空");
    }
    if (value.length() < 2 || value.length() > 50) {
      throw new DomainException("主数据实体编码长度必须在2-50之间");
    }
    if (!value.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
      throw new DomainException("主数据实体编码只能包含字母、数字和下划线，且以字母开头");
    }
  }

  public static MasterDataEntityCode of(String value) {
    return new MasterDataEntityCode(value);
  }
}
