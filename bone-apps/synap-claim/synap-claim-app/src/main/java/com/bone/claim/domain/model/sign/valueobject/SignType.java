package com.bone.claim.domain.model.sign.valueobject;

import java.util.Arrays;

/** 签收方式：自动/人工。 */
public enum SignType {
  AUTO("AUTO", "自动签收"),
  MANUAL("MANUAL", "人工签收");

  private final String code;
  private final String displayName;

  SignType(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  public String code() {
    return code;
  }

  public String displayName() {
    return displayName;
  }

  public static SignType ofCode(String code) {
    return Arrays.stream(values())
        .filter(s -> s.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知签收方式: " + code));
  }
}
