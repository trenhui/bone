package com.bone.claim.domain.model.sign.valueobject;

import java.util.Arrays;
import java.util.Objects;

/** 签收批次状态（四态 + 删除态）。状态迁移只由聚合的 transitionTo 驱动。 */
public enum SignStatus {
  WAIT_FOR_PARTICIPANT("WAIT_FOR_PARTICIPANT", "待上传人员"),
  WAIT_FOR_IMAGE("WAIT_FOR_IMAGE", "待上传影像"),
  WAIT_FOR_CONFIRMATION("WAIT_FOR_CONFIRMATION", "待签收确认"),
  FINISHED("FINISHED", "已签收确认"),
  DELETED("DELETED", "已删除");

  private final String code;
  private final String displayName;

  SignStatus(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  public String code() {
    return code;
  }

  public String displayName() {
    return displayName;
  }

  public boolean terminal() {
    return this == FINISHED || this == DELETED;
  }

  /** 是否可从当前状态迁移到目标状态（幂等同态返回 false，交由调用方判重）。 */
  public boolean canTransitionTo(SignStatus to) {
    Objects.requireNonNull(to, "目标状态不能为空");
    return switch (this) {
      case WAIT_FOR_PARTICIPANT -> to == WAIT_FOR_IMAGE || to == DELETED;
      case WAIT_FOR_IMAGE -> to == WAIT_FOR_CONFIRMATION || to == DELETED;
      case WAIT_FOR_CONFIRMATION -> to == FINISHED || to == DELETED;
      case FINISHED, DELETED -> false;
    };
  }

  public static SignStatus ofCode(String code) {
    return Arrays.stream(values())
        .filter(s -> s.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知签收状态: " + code));
  }
}
