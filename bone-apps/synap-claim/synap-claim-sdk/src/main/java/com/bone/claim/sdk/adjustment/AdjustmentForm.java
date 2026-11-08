package com.bone.claim.sdk.adjustment;

import java.util.Locale;

/**
 * 理算责任形式（跨模块共享的纯 Java 契约，零框架依赖）。
 *
 * <p><b>为什么单独定义在 SDK</b>：{@code app} 的领域枚举 {@code LiabilityRule.LiabilityForm} 带规则装载语义，
 * 不能被引擎模块反向引用。两个模块要对话，只能共同依赖一份不含框架、不含领域实现的枚举。
 *
 * <p><b>非法值拒绝而非回落</b>：回落成报销型会让拼错的配置静默按比例算钱。
 */
public enum AdjustmentForm {
  REIMBURSEMENT,
  FIXED_AMOUNT,
  ALLOWANCE;

  public static AdjustmentForm of(String raw) {
    if (raw == null || raw.isBlank()) {
      return REIMBURSEMENT;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    for (AdjustmentForm form : values()) {
      if (form.name().equals(normalized)) {
        return form;
      }
    }
    throw new IllegalArgumentException("无法识别的责任形式：" + raw);
  }
}
