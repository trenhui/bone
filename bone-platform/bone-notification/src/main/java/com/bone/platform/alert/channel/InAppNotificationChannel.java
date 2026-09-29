package com.bone.platform.alert.channel;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.platform.alert.AlertChannel;
import com.bone.platform.alert.AlertChannelType;
import com.bone.platform.alert.AlertException;
import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.common.NotificationErrorCodes;
import com.bone.platform.alert.common.NotificationErrors;
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
    Long userId =
        message.getContext() != null && message.getContext().get("userId") != null
            ? Long.valueOf(String.valueOf(message.getContext().get("userId")))
            : null;
    Long id = DistributedIdGenerator.generateLongId();
    // 站内信按租户隔离（R9）：租户随告警消息下发（CompositeAlertService 在调用线程捕获），
    // 缺失即失败关闭，绝不回落 0（N-1 修复：不再直读 TenantContext，且修复异步线程丢失租户）。
    Long tenantId = message.getTenantId();
    if (tenantId == null) {
      throw NotificationErrors.of(
          NotificationErrorCodes.TENANT_MISMATCH, "async alert missing tenant context");
    }
    String level = message.getLevel() != null ? message.getLevel().name() : "INFO";
    NotificationMessage notification =
        NotificationMessage.create(
            id, tenantId, message.getTitle(), message.getContent(), level, userId);
    notificationMessageRepository.save(notification);
    log.info("站内信已发送: title={}, userId={}", message.getTitle(), userId);
  }
}
