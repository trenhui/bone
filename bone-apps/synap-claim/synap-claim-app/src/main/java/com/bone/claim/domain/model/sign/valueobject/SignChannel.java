package com.bone.claim.domain.model.sign.valueobject;

import java.util.Arrays;

/** 签收渠道：记录案件来源通道，供审计与筛选。 */
public enum SignChannel {
  INTERNAL_REPORT("INTERNAL_REPORT", "内部报案"),
  API_PUSH("API_PUSH", "API推送"),
  EXPRESS("EXPRESS", "快递"),
  COUNTER("COUNTER", "柜台");

  private final String code;
  private final String displayName;

  SignChannel(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  public String code() {
    return code;
  }

  public String displayName() {
    return displayName;
  }

  public static SignChannel ofCode(String code) {
    return Arrays.stream(values())
        .filter(s -> s.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知签收渠道: " + code));
  }
}
