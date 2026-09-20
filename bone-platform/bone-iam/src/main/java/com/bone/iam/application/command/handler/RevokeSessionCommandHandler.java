package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.RefreshTokenSessionGateway;
import com.bone.iam.domain.session.Session;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 撤销 refresh token（单条 / 账号全部）。 */
@Component
@RequiredArgsConstructor
@Transactional
public class RevokeSessionCommandHandler {

  private final RefreshTokenSessionGateway sessionStore;
  private final AccountAuthorityCache accountAuthorityCache;

  public void revokeOne(Long sessionId) {
    if (sessionId == null) {
      throw BizException.of(400, IamErrorCodes.SESSION_NOT_FOUND);
    }
    Optional<Session> session = sessionStore.findById(sessionId);
    Session target =
        session.orElseThrow(() -> BizException.of(404, IamErrorCodes.SESSION_NOT_FOUND));
    assertSameTenant(target.getTenantId());
    sessionStore.revoke(sessionId);
    accountAuthorityCache.evictAccount(target.getAccountId());
  }

  public int revokeAllForAccount(Long accountId) {
    if (accountId == null) {
      return 0;
    }
    int affected = sessionStore.revokeAllForAccount(accountId);
    accountAuthorityCache.evictAccount(accountId);
    return affected;
  }

  private static void assertSameTenant(Long sessionTenantId) {
    Long current = TenantContext.getTenantIdAsLong();
    if (current == null || current == 0L) {
      return;
    }
    if (sessionTenantId == null || !sessionTenantId.equals(current)) {
      throw BizException.of(403, IamErrorCodes.TENANT_ACCESS_DENIED + ": 跨租户会话操作被拒绝");
    }
  }
}
