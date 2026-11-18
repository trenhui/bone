package com.bone.masterdata.domain.model.supplier;

/**
 * 跨域守卫结论（domain 只消费布尔结论 + 原因码，不感知外部事实来源）。
 *
 * @param passed 守卫是否通过
 * @param reasonCode 原因码，用于审计与错误消息；不得为自由文本描述个人身份信息
 */
public record GuardVerdict(boolean passed, String reasonCode) {

  public GuardVerdict {
    if (reasonCode == null || reasonCode.trim().isEmpty()) {
      throw new IllegalArgumentException("守卫结论必须携带原因码");
    }
  }

  public static GuardVerdict pass(String reasonCode) {
    return new GuardVerdict(true, reasonCode);
  }

  public static GuardVerdict reject(String reasonCode) {
    return new GuardVerdict(false, reasonCode);
  }

  public boolean rejected() {
    return !passed;
  }
}
