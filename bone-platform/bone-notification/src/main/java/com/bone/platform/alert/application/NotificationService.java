package com.bone.platform.alert.application;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.platform.alert.domain.notification.NotificationMessage;
import com.bone.platform.alert.domain.notification.NotificationMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 站内信查询服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private final NotificationMessageRepository notificationMessageRepository;

  public List<NotificationMessage> listByUser(Long userId, int limit) {
    return QueryBuilder.from(NotificationMessage.class)
        .where(NotificationMessage::getUserId)
        .eq(userId)
        .list()
        .stream()
        .limit(Math.max(0, limit))
        .toList();
  }

  public long unreadCount(Long userId) {
    return QueryBuilder.from(NotificationMessage.class)
        .where(NotificationMessage::getUserId)
        .eq(userId)
        .and(NotificationMessage::isRead)
        .eq(false)
        .count();
  }

  @Transactional
  public void markRead(Long id) {
    NotificationMessage message = notificationMessageRepository.findById(id);
    if (message != null && !message.isRead()) {
      message.markRead();
      notificationMessageRepository.save(message);
    }
  }
}
