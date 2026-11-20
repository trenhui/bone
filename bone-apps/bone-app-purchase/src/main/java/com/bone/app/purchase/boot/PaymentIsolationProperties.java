package com.bone.app.purchase.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存量采购付款逃逸处理器的隔离开关（D-06 / F-E）。
 *
 * <p>{@code enabled} 缺省为 {@code false}：未配置 / 缺省 / false 时，{@code purchaseOrderPayHandler} 一律不注册，
 * 使低代码 Runtime 动作无法解析到该 bean，实现"采购域不作付款许可裁决、生产以 PaymentRequested 事件为准"（PA-04）的代码层硬隔离。
 *
 * <p>仅显式 {@code true} 时该处理器才注册，且只作低代码演示 / 占位用途，禁止生产出金。
 */
@ConfigurationProperties(prefix = "procura.purchase-pay-handler")
public class PaymentIsolationProperties {

  /** 是否启用存量付款处理器；缺省关闭（fail-closed）。 */
  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
