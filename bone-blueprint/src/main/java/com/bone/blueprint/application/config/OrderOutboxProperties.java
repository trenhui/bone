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

    private String consumerGroup = "bone-blueprint-order-paid-consumer";
}
