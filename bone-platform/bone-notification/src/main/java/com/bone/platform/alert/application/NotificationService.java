package com.bone.platform.alert.application;

import com.bone.platform.alert.domain.notification.NotificationMessage;
import com.bone.platform.alert.domain.repository.NotificationMessageRepository;
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
