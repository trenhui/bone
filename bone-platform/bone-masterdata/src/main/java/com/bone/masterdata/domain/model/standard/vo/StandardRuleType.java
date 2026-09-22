package com.bone.masterdata.domain.model.standard.vo;

import com.bone.core.exception.DomainException;

/** 数据标准规则类型。 */
public enum StandardRuleType {
  ENCODING(1),
  REFERENCE(2);

  private final int code;

  StandardRuleType(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static StandardRuleType of(int code) {
    for (StandardRuleType t : values()) {
      if (t.code == code) {
        return t;
      }
    }
    throw new DomainException("未知标准规则类型: " + code);
  }
}
