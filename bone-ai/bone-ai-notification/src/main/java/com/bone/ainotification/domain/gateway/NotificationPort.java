package com.bone.ainotification.domain.gateway;

import com.bone.ainotification.domain.model.AlertEvent;
import com.bone.ainotification.domain.model.NotificationChannel;
import com.bone.ainotification.domain.model.NotificationResult;

/**
 * 通知外发端口（IM/邮件/Webhook/SMS 由基础设施适配）。
 *
 * <p>消费方可提供自有 {@code @Bean NotificationPort} 覆盖默认 Webhook 实现，接企业 IM / 短信网关等。
 */
public interface NotificationPort {
  NotificationResult dispatch(AlertEvent event, NotificationChannel channel, String target);
}
