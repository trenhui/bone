package com.bone.procurement.governance.domain.model;

import java.util.Objects;

/**
 * AI 能力码：治理登记的唯一标识（对应蓝图能力卡 G/M 编号与 D3-v2 §1.1 能力账本）。
 *
 * <p>值对象不信任外部输入，构造即归一与校验；空白能力码一律拒绝，避免空串绕过 fail-closed 判定。
 */
public record CapabilityCode(String value) {

  public CapabilityCode {
    Objects.requireNonNull(value, "capabilityCode");
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("能力码不得为空");
    }
    if (normalized.length() > 64) {
      throw new IllegalArgumentException("能力码不得超过 64 字符");
    }
    value = normalized;
  }

  @Override
  public String toString() {
    return value;
  }
}
