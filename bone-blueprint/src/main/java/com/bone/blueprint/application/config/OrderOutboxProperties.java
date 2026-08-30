package com.bone.blueprint.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bone.blueprint.outbox")
public class OrderOutboxProperties {

  private boolean enabled = true;

  /** true 时通过 RocketMQ 中继；false 时仅结构化日志（开发默认）。 */
  private boolean mqEnabled = false;

  private int batchSize = 50;

  private int maxRetries = 5;

  private String orderPaidTopic = "bone.order.paid";

  /** 「钱货不一致」告警/补偿事件主题：支付成功但订单无法确认支付时投递。 */
  private String paymentInconsistentTopic = "bone.order.payment-inconsistent";

  private String consumerGroup = "bone-blueprint-order-paid-consumer";
}
