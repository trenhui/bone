package com.bone.app.purchase.boot;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动期硬隔离状态报告器（D-06 可观察证据）：始终注册，按开关输出启用 / 禁用日志。
 *
 * <p>独立于被隔离的付款处理器，缺省禁用态日志也一定会出现，使"缺省即禁用"可从启动日志观察与审计。
 */
public class LegacyPaymentIsolationReporter {

  private static final Logger log = LoggerFactory.getLogger(LegacyPaymentIsolationReporter.class);

  private final PaymentIsolationProperties properties;

  public LegacyPaymentIsolationReporter(PaymentIsolationProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void report() {
    if (properties.isEnabled()) {
      log.warn(
          "[procura-hard-isolation] 存量采购付款处理器已显式启用（procura.purchase-pay-handler.enabled=true）；"
              + "仅为低代码演示/占位，禁止用于生产出金，生产付款以 PaymentRequested 事件链路为准（PA-04）。");
    } else {
      log.info(
          "[procura-hard-isolation] 存量采购付款处理器已硬隔离/禁用（缺省即 fail-closed）；"
              + "Runtime 动作无法触发付款，生产付款以 PaymentRequested 事件链路为准（PA-04）。");
    }
  }
}
