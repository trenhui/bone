package com.bone.airules.domain.service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则加载熔断器（G12 控制矩阵「加载失败熔断」）：验签失败 / 版本缺失时打开并记录原因。
 *
 * <p>只产出确定性兜底标记（FAIL_CLOSED / NORMAL），不改写资金路径；默认不装配，仅在 verified 模式消费。
 */
public class RuleLoadCircuitBreaker {

  private final AtomicReference<String> openReason = new AtomicReference<>();

  /** 记录一次拒绝并打开熔断（首个原因胜出，避免并发覆盖）。 */
  public void reject(String reason) {
    openReason.compareAndSet(null, reason);
  }

  /** 复位为正常（仅在有明确恢复信号时调用）。 */
  public void reset() {
    openReason.set(null);
  }

  public boolean isOpen() {
    return openReason.get() != null;
  }

  public String reason() {
    return openReason.get();
  }

  /** 兜底标记：熔断打开为 FAIL_CLOSED，否则 NORMAL。 */
  public String status() {
    return isOpen() ? "FAIL_CLOSED" : "NORMAL";
  }
}
