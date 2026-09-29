package com.bone.platform.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.platform.alert.common.NotificationErrorCodes;
import com.bone.platform.alert.domain.gateway.TenantProvider;
import com.bone.platform.alert.domain.model.notification.NotificationMessage;
import com.bone.platform.alert.domain.repository.NotificationMessageRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 站内信应用服务：租户闭环 + IDOR 归属断言 + 入参校验。 */
@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

  @Mock private NotificationMessageRepository repository;
  @Mock private TenantProvider tenantProvider;

  private NotificationApplicationService service;

  @BeforeEach
  void setUp() {
    service = new NotificationApplicationService(repository, tenantProvider);
  }

  @Test
  void markReadSucceedsWhenTenantAndUserMatch() {
    NotificationMessage message = NotificationMessage.create(1L, 100L, "t", "c", "INFO", 42L);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(repository.findByIdAndTenant(1L, 100L)).thenReturn(message);

    service.markRead(1L, 42L);

    verify(repository, times(1)).save(message);
    assertThat(message.isRead()).isTrue();
  }

  @Test
  void markReadThrowsNotFoundWhenAbsent() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(repository.findByIdAndTenant(2L, 100L)).thenReturn(null);

    assertThatThrownBy(() -> service.markRead(2L, 42L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.NOT_FOUND));
    verify(repository, never()).save(any(NotificationMessage.class));
  }

  @Test
  void markReadThrowsAccessDeniedWhenUserMismatch() {
    NotificationMessage message = NotificationMessage.create(1L, 100L, "t", "c", "INFO", 999L);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(repository.findByIdAndTenant(1L, 100L)).thenReturn(message);

    assertThatThrownBy(() -> service.markRead(1L, 42L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.ACCESS_DENIED));
    verify(repository, never()).save(any(NotificationMessage.class));
  }

  @Test
  void markReadThrowsTenantMismatchWhenContextMissing() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);

    assertThatThrownBy(() -> service.markRead(1L, 42L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.TENANT_MISMATCH));
  }

  @Test
  void listByUserRejectsNullUserId() {
    assertThatThrownBy(() -> service.listByUser(null, 10))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCodes.INVALID_PARAM));
  }

  @Test
  void listByUserCapsLimitAtMax() {
    NotificationMessage m = NotificationMessage.create(1L, 100L, "t", "c", "INFO", 42L);
    when(repository.findByUserId(42L)).thenReturn(List.of(m, m, m, m, m));

    List<NotificationMessage> result = service.listByUser(42L, 3);
    assertThat(result).hasSize(3);
    verify(repository).findByUserId(42L);
  }
}
