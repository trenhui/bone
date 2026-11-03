package com.bone.claim.domain.model.mapping;

/** 匹配规则：用于回答"这个映射是怎么来的"，也是复核抽样分层的依据。 */
public enum MatchRule {
  CODE_EQUAL("CODE_EQUAL"),
  CREDIT_CODE("CREDIT_CODE"),
  NAME_NORM("NAME_NORM"),
  FUZZY("FUZZY"),
  MANUAL("MANUAL"),
  AI_SUGGEST("AI_SUGGEST");

  private final String code;

  MatchRule(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static MatchRule fromCode(String code) {
    for (MatchRule value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知匹配规则: " + code);
  }
}
