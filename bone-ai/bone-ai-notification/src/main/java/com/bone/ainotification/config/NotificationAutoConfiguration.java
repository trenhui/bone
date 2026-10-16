package com.bone.ainotification.config;

import com.bone.ainotification.application.AlertNotificationService;
import com.bone.ainotification.domain.gateway.NotificationAckSink;
import com.bone.ainotification.domain.gateway.NotificationPort;
import com.bone.ainotification.infrastructure.notification.LoggingNotificationAckSink;
import com.bone.ainotification.infrastructure.notification.WebhookNotificationAdapter;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-notification 自动配置（Boot 3 {@code .imports} 机制）。
 *
 * <p>默认提供 Webhook 外发适配器（URL 取自 {@code bone.ai.notification.webhook-url}）与日志型 ack 回写； 消费方均可用
 * {@code @Bean} 覆盖。本模块为纯端口编排，不引入新 DDL 表。
 */
@AutoConfiguration
public class NotificationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public NotificationPort notificationPort(
      @Value("${bone.ai.notification.webhook-url:}") String webhookUrl) {
    return new WebhookNotificationAdapter(HttpClient.newHttpClient(), webhookUrl);
  }

  @Bean
  @ConditionalOnMissingBean
  public NotificationAckSink notificationAckSink() {
    return new LoggingNotificationAckSink();
  }

  @Bean
  @ConditionalOnMissingBean
  public AlertNotificationService alertNotificationService(
      NotificationPort notificationPort, NotificationAckSink ackSink) {
    return new AlertNotificationService(notificationPort, ackSink);
  }
}
