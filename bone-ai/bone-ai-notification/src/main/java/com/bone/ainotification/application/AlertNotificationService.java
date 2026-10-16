package com.bone.ainotification.application;

import com.bone.ainotification.domain.gateway.NotificationAckSink;
import com.bone.ainotification.domain.gateway.NotificationPort;
import com.bone.ainotification.domain.model.AlertEvent;
import com.bone.ainotification.domain.model.NotificationAck;
import com.bone.ainotification.domain.model.NotificationChannel;
import com.bone.ainotification.domain.model.NotificationResult;

/**
 * 消警闭环服务：触发（AlertEvent）→ 外发（NotificationPort）→ 外部 ack → 回写（NotificationAckSink）。
 *
 * <p>本服务只编排端口，不含外部系统耦合；具体通道与回写目标由基础设施/消费方提供。
 */
public class AlertNotificationService {

  private final NotificationPort notificationPort;
  private final NotificationAckSink ackSink;

  public AlertNotificationService(NotificationPort notificationPort, NotificationAckSink ackSink) {
    this.notificationPort = notificationPort;
    this.ackSink = ackSink;
  }

  public NotificationResult dispatch(AlertEvent event, NotificationChannel channel, String target) {
    return notificationPort.dispatch(event, channel, target);
  }

  public void acknowledge(NotificationAck ack) {
    ackSink.recordAck(ack);
  }
}
