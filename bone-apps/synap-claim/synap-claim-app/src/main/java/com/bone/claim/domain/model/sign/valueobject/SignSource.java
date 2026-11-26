package com.bone.claim.domain.model.sign.valueobject;

import java.util.Arrays;

/** 签收来源：线上/线下。 */
public enum SignSource {
  ONLINE("ONLINE", "线上"),
  OFFLINE("OFFLINE", "线下");

  private final String code;
  private final String displayName;

  SignSource(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  public String code() {
    return code;
  }

  public String displayName() {
    return displayName;
  }

  public static SignSource ofCode(String code) {
    return Arrays.stream(values())
        .filter(s -> s.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知签收来源: " + code));
  }
}
