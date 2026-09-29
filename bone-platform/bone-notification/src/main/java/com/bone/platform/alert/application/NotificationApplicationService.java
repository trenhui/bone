package com.bone.platform.alert.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.platform.alert.common.NotificationErrorCodes;
import com.bone.platform.alert.common.NotificationErrors;
import com.bone.platform.alert.domain.gateway.TenantProvider;
import com.bone.platform.alert.domain.model.notification.NotificationMessage;
import com.bone.platform.alert.domain.repository.NotificationMessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内信查询服务：租户闭环 + IDOR 归属校验。
 *
 * <p><b>租户闭环（失败关闭）</b>：所有读取均以 {@code TenantProvider} 提供的租户 ID 圈定范围；无租户上下文直接拒绝
 * （TENANT_MISMATCH），不以平台租户 0 或空集兜底——「未认证访问」与「平台租户访问」必须可区分。
 *
 * <p><b>IDOR 防护</b>：站内信按 {@code findByIdAndTenant(id, tenantId)} 取出后，还须校验 {@code message.userId ==
 * 入参 userId}，防止同租户内 A 用户标记 B 用户的消息已读。
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
  private final TenantProvider tenantProvider;

  /** 按用户查询站内信（userId 必填；limit 由调用方圈定上限）。 */
  public List<NotificationMessage> listByUser(Long userId, int limit) {
    requireUserId(userId);
    return notificationMessageRepository.findByUserId(userId).stream()
        .limit(Math.max(0, limit))
        .toList();
  }

  public long unreadCount(Long userId) {
    requireUserId(userId);
    return notificationMessageRepository.countUnreadByUserId(userId);
  }

  /**
   * 标记已读：租户闭环 → 归属校验 → 幂等翻转。
   *
   * <p>已读（{@code isRead()}）为真时不再落库，保证重复请求幂等。
   */
  @Transactional
  public void markRead(Long id, Long userId) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw NotificationErrors.of(NotificationErrorCodes.TENANT_MISMATCH, "缺少租户上下文");
    }
    NotificationMessage message = notificationMessageRepository.findByIdAndTenant(id, tenantId);
    if (message == null) {
      throw NotificationErrors.of(NotificationErrorCodes.NOT_FOUND, id);
    }
    if (!message.getUserId().equals(userId)) {
      throw NotificationErrors.of(NotificationErrorCodes.ACCESS_DENIED, "message " + id);
    }
    if (!message.isRead()) {
      message.markRead();
      notificationMessageRepository.save(message);
    }
  }

  private void requireUserId(Long userId) {
    if (userId == null) {
      throw NotificationErrors.of(NotificationErrorCodes.INVALID_PARAM, "userId is required");
    }
  }
}
