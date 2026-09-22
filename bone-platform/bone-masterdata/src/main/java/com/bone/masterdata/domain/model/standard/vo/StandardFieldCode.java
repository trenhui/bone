package com.bone.masterdata.domain.model.standard.vo;

import com.bone.core.exception.DomainException;

/** 标准字段编码值对象。 */
public record StandardFieldCode(String value) {
  public StandardFieldCode {
    if (value == null || value.isBlank()) {
      throw new DomainException("标准字段编码不能为空");
    }
    if (value.length() > 128) {
      throw new DomainException("标准字段编码长度不能超过 128");
    }
  }

  public static StandardFieldCode of(String value) {
    return new StandardFieldCode(value);
  }
}
