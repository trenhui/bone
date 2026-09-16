package com.bone.blueprint.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Outbox 中继配置。 */
@Data
@ConfigurationProperties(prefix = "bone.blueprint.outbox")
public class OrderOutboxProperties {

  private boolean enabled = true;

  /** true 时通过 RocketMQ 中继；false 时仅结构化日志（开发默认）。 */
  private boolean mqEnabled = false;

  private int batchSize = 50;

  private int maxRetries = 5;

  private String orderPaidTopic = "bone.order.paid";

  /** 「支付已成功」事实主题：与支付单状态变更同事务落库后中继（资金事实，不可容忍丢失）。 */
  private String paymentSucceededTopic = "bone.payment.succeeded";

  /** 「钱货不一致」告警/补偿事件主题：支付成功但订单无法确认支付时投递。 */
  private String paymentInconsistentTopic = "bone.order.payment-inconsistent";

  /** 「支付已退款」主题：退款确认同事务落库后中继。 */
  private String paymentRefundedTopic = "bone.payment.refunded";

  /** 「支付已失败」主题：支付单置 FAILED 同事务落库后中继，下游用于通知用户与告警。 */
  private String paymentFailedTopic = "bone.payment.failed";

  private String consumerGroup = "bone-blueprint-order-paid-consumer";
}
