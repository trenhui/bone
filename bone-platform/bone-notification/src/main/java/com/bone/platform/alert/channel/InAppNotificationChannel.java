package com.bone.platform.alert.channel;

import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.platform.alert.AlertChannel;
import com.bone.platform.alert.AlertChannelType;
import com.bone.platform.alert.AlertException;
import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.domain.model.notification.NotificationMessage;
import com.bone.platform.alert.domain.repository.NotificationMessageRepository;
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
      // 站内信按租户隔离（R9）：租户取自当前请求上下文；异步告警需保证 TenantContext 已传播。
      Long tenantId = TenantContext.getTenantIdAsLong();
      NotificationMessage notification =
          NotificationMessage.create(
              id,
              tenantId,
              message.getTitle(),
              message.getContent(),
              message.getLevel().name(),
              userId);
      notificationMessageRepository.save(notification);
      log.info("站内信已发送: title={}, userId={}", message.getTitle(), userId);
    } catch (Exception e) {
      throw new AlertException("站内信发送失败: " + e.getMessage(), e);
    }
  }
}
