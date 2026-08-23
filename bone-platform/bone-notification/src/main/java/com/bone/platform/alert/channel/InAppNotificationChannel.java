package com.bone.platform.alert.channel;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.platform.alert.AlertChannel;
import com.bone.platform.alert.AlertChannelType;
import com.bone.platform.alert.AlertException;
import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.domain.notification.NotificationMessage;
import com.bone.platform.alert.domain.notification.NotificationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 站内信通道：将告警落库为 {@link NotificationMessage}。 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    value = "alert.channels.inapp.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class InAppNotificationChannel implements AlertChannel {

  private final NotificationMessageRepository notificationMessageRepository;

  @Override
  public AlertChannelType channelType() {
    return AlertChannelType.IN_APP;
  }

  @Override
  public void send(AlertMessage message) throws AlertException {
    try {
      Long userId =
          message.getContext() != null && message.getContext().get("userId") != null
              ? Long.valueOf(String.valueOf(message.getContext().get("userId")))
              : null;
      Long id = DistributedIdGenerator.generateLongId();
      NotificationMessage notification =
          NotificationMessage.create(
              id, message.getTitle(), message.getContent(), message.getLevel().name(), userId);
      notificationMessageRepository.save(notification);
      log.info("站内信已发送: title={}, userId={}", message.getTitle(), userId);
    } catch (Exception e) {
      throw new AlertException("站内信发送失败: " + e.getMessage(), e);
    }
  }
}
