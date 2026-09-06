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
    // 该用户未读站内信数。QueryBuilder 的 and().eq(false).count() 组合存在异常，
    // 改为「按用户拉取 + 内存过滤未读」，数据量小且 list() 已验证可用。
    return QueryBuilder.from(NotificationMessage.class)
        .where(NotificationMessage::getUserId)
        .eq(userId)
        .list()
        .stream()
        .filter(m -> !m.isRead())
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
