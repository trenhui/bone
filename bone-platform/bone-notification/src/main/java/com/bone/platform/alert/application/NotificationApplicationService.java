package com.bone.platform.alert.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.platform.alert.domain.model.notification.NotificationMessage;
import com.bone.platform.alert.domain.repository.NotificationMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内信查询服务。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务仅做站内信读取与已读标记，聚合（NotificationMessage）
 * 当前不发布领域事件；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@NoDomainEvent
public class NotificationApplicationService {

  private final NotificationMessageRepository notificationMessageRepository;

  public List<NotificationMessage> listByUser(Long userId, int limit) {
    return notificationMessageRepository.findByUserId(userId).stream()
        .limit(Math.max(0, limit))
        .toList();
  }

  public long unreadCount(Long userId) {
    return notificationMessageRepository.countUnreadByUserId(userId);
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
