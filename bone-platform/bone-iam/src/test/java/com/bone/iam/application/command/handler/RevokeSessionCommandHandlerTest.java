package com.bone.iam.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.RefreshTokenSessionStore;
import com.bone.iam.domain.session.Session;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeSessionCommandHandlerTest {

  @Mock RefreshTokenSessionStore sessionStore;

  @Mock AccountAuthorityCache accountAuthorityCache;

  @InjectMocks RevokeSessionCommandHandler handler;

  @AfterEach
  void cleanCtx() {
    TenantContext.clear();
  }

  @Test
  void revokeOneEvictsAuthorityCache() {
    Session session =
        Session.builder()
            .id(7L)
            .accountId(99L)
            .tenantId(0L)
            .revoked(false)
            .expiresAt(LocalDateTime.now().plusHours(1))
            .createdAt(LocalDateTime.now())
            .build();
    when(sessionStore.findById(7L)).thenReturn(Optional.of(session));
    when(sessionStore.revoke(7L)).thenReturn(1);

    handler.revokeOne(7L);

    verify(sessionStore).revoke(7L);
    verify(accountAuthorityCache).evictAccount(99L);
  }

  @Test
  void revokeAllCascadesToCache() {
    when(sessionStore.revokeAllForAccount(99L)).thenReturn(3);

    int affected = handler.revokeAllForAccount(99L);

    assertThat(affected).isEqualTo(3);
    verify(accountAuthorityCache).evictAccount(99L);
  }

  @Test
  void crossTenantRevokeRejected() {
    TenantContext.setTenantId(2L);
    Session session =
        Session.builder()
            .id(7L)
            .accountId(99L)
            .tenantId(1L)
            .revoked(false)
            .expiresAt(LocalDateTime.now().plusHours(1))
            .createdAt(LocalDateTime.now())
            .build();
    when(sessionStore.findById(7L)).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> handler.revokeOne(7L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(IamErrorCodes.TENANT_ACCESS_DENIED);
    verify(sessionStore, never()).revoke(eq(7L));
  }

  @Test
  void missingSessionThrows404() {
    when(sessionStore.findById(7L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.revokeOne(7L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(IamErrorCodes.SESSION_NOT_FOUND);
  }
}
