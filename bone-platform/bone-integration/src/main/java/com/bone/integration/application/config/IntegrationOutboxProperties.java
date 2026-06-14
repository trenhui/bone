package com.bone.integration.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Outbox 中继策略（应用层配置，由 infrastructure 绑定到 Spring 环境）。 */
@Data
@ConfigurationProperties(prefix = "bone.integration.outbox")
public class IntegrationOutboxProperties {

  /** 是否在业务事务内写 int_outbox。 */
  private boolean enabled = true;

  /** true 时通过 RocketMQ 中继；false 时仅结构化日志（开发默认）。 */
  private boolean mqEnabled = false;

  private int batchSize = 50;

  private int maxRetries = 5;

  /** 中继轮询间隔（毫秒）。 */
  private long relayIntervalMs = 5000L;
}
