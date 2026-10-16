package com.bone.ainotification.domain.gateway;

import com.bone.ainotification.domain.model.NotificationAck;

/**
 * ack 回写端口（消警闭环末端）。
 *
 * <p>默认实现仅记日志；消费方可 {@code @Bean NotificationAckSink} 覆盖为写入自有告警/工单系统。
 */
public interface NotificationAckSink {
  void recordAck(NotificationAck ack);
}
