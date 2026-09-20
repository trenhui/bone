package com.bone.iam.application;

import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.RefreshTokenSessionGateway;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.session.Session;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话应用服务（Application Service First 收敛点）——会话吊销 / 列表用例的唯一入口。
 *
 * <p>原 {@code RevokeSessionCommandHandler} 与 {@code SessionListQueryHandler} 逻辑已全量内联于此（E-3.11
 * 一次性大爆炸收敛）。 Controller 只依赖本类。各方法语义 / 异常 / 事务边界与原 Handler 完全一致（HTTP 契约不变）。
 *
 * <p>调用方租户经 {@link TenantProvider} 端口读取，本类不直取 {@code TenantContext}（E-4.4）。
 */
@Service
@RequiredArgsConstructor
public class SessionApplicationService {

  private final RefreshTokenSessionGateway sessionStore;
  private final AccountAuthorityCache accountAuthorityCache;
  private final TenantProvider tenantProvider;

  /** 撤销 refresh token（单条）。 */
  @Transactional
  public void revokeOne(Long sessionId) {
    if (sessionId == null) {
      throw IamErrors.of(IamErrorCodes.SESSION_ID_REQUIRED, "会话ID不能为空");
    }
    Optional<Session> session = sessionStore.findById(sessionId);
    Session target = session.orElseThrow(() -> IamErrors.of(IamErrorCodes.SESSION_NOT_FOUND));
    assertSameTenant(target.getTenantId());
    sessionStore.revoke(sessionId);
    accountAuthorityCache.evictAccount(target.getAccountId());
  }

  /** 撤销 refresh token（账号全部）。 */
  @Transactional
  public int revokeAllForAccount(Long accountId) {
    if (accountId == null) {
      return 0;
    }
    int affected = sessionStore.revokeAllForAccount(accountId);
    accountAuthorityCache.evictAccount(accountId);
    return affected;
  }

  /** 列出账号活跃 / 历史 refresh token。 */
  @Transactional(readOnly = true)
  public List<Session> list(Long accountId) {
    List<Session> sessions = sessionStore.listByAccountId(accountId);
    Long current = tenantProvider.currentTenantIdOrNull();
    if (current == null || current == 0L) {
      return sessions;
    }
    // 非平台租户只能看到本租户的会话；防越权（详设 §3.4）。
    return sessions.stream()
        .filter(s -> s.getTenantId() != null && s.getTenantId().equals(current))
        .toList();
  }

  private void assertSameTenant(Long sessionTenantId) {
    Long current = tenantProvider.currentTenantIdOrNull();
    if (current == null || current == 0L) {
      return;
    }
    if (sessionTenantId == null || !sessionTenantId.equals(current)) {
      throw IamErrors.of(IamErrorCodes.TENANT_ACCESS_DENIED, "跨租户会话操作被拒绝");
    }
  }
}
