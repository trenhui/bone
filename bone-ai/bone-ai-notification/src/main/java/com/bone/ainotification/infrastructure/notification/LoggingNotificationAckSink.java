package com.bone.ainotification.infrastructure.notification;

import com.bone.ainotification.domain.gateway.NotificationAckSink;
import com.bone.ainotification.domain.model.NotificationAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认 ack 回写：仅记日志（消费方可 {@code @Bean} 覆盖为接自有系统）。 */
public class LoggingNotificationAckSink implements NotificationAckSink {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAckSink.class);

  @Override
  public void recordAck(NotificationAck ack) {
    log.info("预警已确认 alertId={} ackedBy={} note={}", ack.alertId(), ack.ackedBy(), ack.note());
  }
}
