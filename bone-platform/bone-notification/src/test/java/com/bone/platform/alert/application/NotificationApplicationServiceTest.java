package com.bone.platform.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.platform.alert.common.NotificationErrorCodes;
import com.bone.platform.alert.domain.gateway.TenantProvider;
import com.bone.platform.alert.domain.model.notification.NotificationMessage;
import com.bone.platform.alert.domain.repository.NotificationMessageRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 站内信应用服务：租户闭环 + 归属以 JWT 为准 + 入参校验。
 *
 * <p><b>归属基准是认证上下文而非入参（2026-10-03 IDOR 修正的回归护栏）</b>：userId 只能来自 {@code SecurityContextHolder} 里的
 * {@link JwtPrincipal}。本测试类因此不再向服务传任何 userId， 只通过 {@link #loginAs(String)} 布置主体 —— 若将来有人又把userId
 * 改成入参，{@link #markReadRejectsWhenNoPrincipal()} 这类"无主体即拒绝"的断言会先失效，构不成反向保护， 故额外用 {@link
 * #attackerCannotReadOthersMessageByPassingUserId()} 显式钉住该行为。
 */
@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

  @Mock private NotificationMessageRepository repository;
  @Mock private TenantProvider tenantProvider;

  private NotificationApplicationService service;

  @BeforeEach
  void setUp() {
    service = new NotificationApplicationService(repository, tenantProvider);
  }

  /** 每个用例后清空认证上下文：SecurityContextHolder 是线程局部单例，残留会污染后续用例。 */
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void loginAs(String userId) {
    JwtPrincipal principal = new JwtPrincipal(userId, "tester", "100", List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  @Test
  void markReadSucceedsWhenTenantAndUserMatch() {
    loginAs("42");
    NotificationMessage message = NotificationMessage.create(1L, 100L, "t", "c", "INFO", 42L);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(repository.findByIdAndTenant(1L, 100L)).thenReturn(message);

    service.markRead(1L);

    verify(repository, times(1)).save(message);
    assertThat(message.isRead()).isTrue();
  }

  @Test
  void markReadThrowsNotFoundWhenAbsent() {
    loginAs("42");
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(repository.findByIdAndTenant(2L, 100L)).thenReturn(null);

    assertThatThrownBy(() -> service.markRead(2L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.NOT_FOUND));
    verify(repository, never()).save(any(NotificationMessage.class));
  }

  @Test
  void markReadThrowsAccessDeniedWhenUserMismatch() {
    loginAs("42");
    NotificationMessage message = NotificationMessage.create(1L, 100L, "t", "c", "INFO", 999L);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(repository.findByIdAndTenant(1L, 100L)).thenReturn(message);

    assertThatThrownBy(() -> service.markRead(1L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.ACCESS_DENIED));
    verify(repository, never()).save(any(NotificationMessage.class));
  }

  @Test
  void markReadThrowsTenantMismatchWhenContextMissing() {
    loginAs("42");
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);

    assertThatThrownBy(() -> service.markRead(1L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.TENANT_MISMATCH));
  }

  /**
   * IDOR 回归护栏：他人的站内信即便ID 与租户都对得上，归属校验也必须按 JWT 主体拒绝。
   *
   * <p>原实现把 userId 当入参传进来，攻击者传自己的 42 就能读过别人 999 的信；现在基准不可由调用方指定。
   */
  @Test
  void attackerCannotReadOthersMessageByPassingUserId() {
    loginAs("42");
    NotificationMessage message = NotificationMessage.create(1L, 100L, "t", "c", "INFO", 999L);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(repository.findByIdAndTenant(1L, 100L)).thenReturn(message);

    assertThatThrownBy(() -> service.markRead(1L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.ACCESS_DENIED));
    assertThat(message.isRead()).isFalse();
  }

  /** 无认证主体时失败关闭：不得回落到 0 / null / "system" 之类的默认值。 */
  @Test
  void markReadRejectsWhenNoPrincipal() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);

    assertThatThrownBy(() -> service.markRead(1L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.ACCOUNT_REQUIRED));
    verify(repository, never()).findByIdAndTenant(any(), any());
  }

  @Test
  void listRejectsWhenNoPrincipal() {
    assertThatThrownBy(() -> service.listByCurrentUser(10))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.ACCOUNT_REQUIRED));
    verify(repository, never()).findByUserId(any());
  }

  @Test
  void listRejectsWhenPrincipalUserIdIsBlank() {
    loginAs("  ");

    assertThatThrownBy(() -> service.listByCurrentUser(10))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.ACCOUNT_REQUIRED));
  }

  @Test
  void listRejectsWhenPrincipalUserIdIsNotNumeric() {
    loginAs("not-a-number");

    assertThatThrownBy(() -> service.listByCurrentUser(10))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.ACCOUNT_REQUIRED));
  }

  @Test
  void listByCurrentUserCapsLimitAtMax() {
    loginAs("42");
    NotificationMessage m = NotificationMessage.create(1L, 100L, "t", "c", "INFO", 42L);
    when(repository.findByUserId(42L)).thenReturn(List.of(m, m, m, m, m));

    List<NotificationMessage> result = service.listByCurrentUser(3);
    assertThat(result).hasSize(3);
    verify(repository).findByUserId(42L);
  }

  @Test
  void unreadCountByCurrentUserDelegatesToJwtUser() {
    loginAs("42");
    when(repository.countUnreadByUserId(42L)).thenReturn(3L);

    assertThat(service.unreadCountByCurrentUser()).isEqualTo(3L);
    verify(repository).countUnreadByUserId(42L);
  }
}
