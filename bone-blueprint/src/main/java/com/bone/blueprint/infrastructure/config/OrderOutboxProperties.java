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

  /** Topic 命名遵循《Bone-消息与事件规范》§2：{@code {scope}.{domain}.{resource}_{action}.v{major}}。 */
  private String orderPaidTopic = "domain.order.order_paid.v1";

  /** 「支付已成功」事实主题：与支付单状态变更同事务落库后中继（资金事实，不可容忍丢失）。 */
  private String paymentSucceededTopic = "domain.payment.payment_succeeded.v1";

  /** 「钱货不一致」告警/补偿事件主题：支付成功但订单无法确认支付时投递。 */
  private String paymentInconsistentTopic = "domain.order.order_payment_inconsistent.v1";

  /** 「支付已退款」主题：退款确认同事务落库后中继。 */
  private String paymentRefundedTopic = "domain.payment.payment_refunded.v1";

  /** 「支付已失败」主题：支付单置 FAILED 同事务落库后中继，下游用于通知用户与告警。 */
  private String paymentFailedTopic = "domain.payment.payment_failed.v1";

  /** 死信主题：中继重试超限后转投，人工/工具重放（消息与事件规范 §6）。 */
  private String deadLetterTopic = "platform.dead_letter.v1";

  private String consumerGroup = "bone-blueprint-order-paid-consumer";
}
