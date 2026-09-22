package com.bone.system.domain.model.dict.vo;

import com.bone.core.exception.BizException;

/** 字典类型值对象。 */
public record DictType(String value) {
  public DictType {
    if (value == null || value.isBlank()) {
      throw BizException.of("字典类型不能为空");
    }
    if (value.length() > 64) {
      throw BizException.of("字典类型长度不能超过 64");
    }
  }

  public static DictType of(String value) {
    return new DictType(value);
  }
}
