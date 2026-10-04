package com.bone.platform.alert.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.security.auth.CurrentAccountResolver;
import com.bone.core.security.jwt.JwtPrincipal;
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
 * <p><b>IDOR 防护（2026-10-03 修正：归属基准从入参改为 JWT）</b>：本服务原先接收 {@code userId} 入参， 并用 {@code
 * message.getUserId().equals(userId)} 校验归属 —— 但这个 {@code userId} 是**调用方给的 （原为
 * {@code @RequestParam}）**，攻击者传自己的 ID 即可通过校验，"归属校验"等于没有。 现{@code userId} 一律经 {@link
 * CurrentAccountResolver} 从 JWT 主体解析，调用方无法指定； 无有效主体直接拒绝（ACCOUNT_REQUIRED），不再接受任何来自请求参数的用户标识。
 *
 * <p><b>为何不再保留 userId 入参</b>：保留一个"看起来能限定用户、实则由调用方指定"的参数， 就是给未来留一个重新引入 IDOR
 * 的口子。站内信是私有资源，归属只能来自认证上下文。
 *
 * <p><b>不发DomainEvent 豁免（E-5.4）</b>：本服务仅做站内信读取与已读标记，聚合（NotificationMessage）
 * 当前不发布领域事件；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@NoDomainEvent
public class NotificationApplicationService {

  private final NotificationMessageRepository notificationMessageRepository;
  private final TenantProvider tenantProvider;

  /** 按当前登录用户查询站内信（limit 由调用方圈定上限）。 */
  public List<NotificationMessage> listByCurrentUser(int limit) {
    return notificationMessageRepository.findByUserId(requireCurrentUserId()).stream()
        .limit(Math.max(0, limit))
        .toList();
  }

  public long unreadCountByCurrentUser() {
    return notificationMessageRepository.countUnreadByUserId(requireCurrentUserId());
  }

  /**
   * 标记已读：租户闭环 → 归属校验 → 幂等翻转。
   *
   * <p>已读（{@code isRead()}）为真时不再落库，保证重复请求幂等。
   */
  @Transactional
  public void markRead(Long id) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw NotificationErrors.of(NotificationErrorCodes.TENANT_MISMATCH, "缺少租户上下文");
    }
    Long currentUserId = requireCurrentUserId();
    NotificationMessage message = notificationMessageRepository.findByIdAndTenant(id, tenantId);
    if (message == null) {
      throw NotificationErrors.of(NotificationErrorCodes.NOT_FOUND, id);
    }
    if (!message.getUserId().equals(currentUserId)) {
      throw NotificationErrors.of(NotificationErrorCodes.ACCESS_DENIED, "message " + id);
    }
    if (!message.isRead()) {
      message.markRead();
      notificationMessageRepository.save(message);
    }
  }

  /**
   * 当前登录用户 ID；无有效 JWT 主体即拒绝。
   *
   * <p><b>失败关闭，不做兜底</b>：站内信是私有资源，无主体时返回任何默认值都等于把"读别人的信" 变成合法行为。这里与 {@code TenantProvider} 同一口径 ——
   * 缺失就是缺失。
   */
  private Long requireCurrentUserId() {
    return CurrentAccountResolver.currentPrincipal()
        .map(JwtPrincipal::userId)
        .filter(id -> id != null && !id.isBlank())
        .map(
            id -> {
              try {
                return Long.valueOf(id);
              } catch (NumberFormatException e) {
                throw NotificationErrors.of(NotificationErrorCodes.ACCOUNT_REQUIRED, "无效的会话主体");
              }
            })
        .orElseThrow(
            () -> NotificationErrors.of(NotificationErrorCodes.ACCOUNT_REQUIRED, "缺少已认证的登录主体"));
  }
}
