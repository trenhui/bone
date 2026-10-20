package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.iam.application.command.RefreshTokenCommand;
import com.bone.iam.application.config.IamPasswordProperties;
import com.bone.iam.application.port.out.PasswordEncoderPort;
import com.bone.iam.application.port.out.TokenBlacklistPort;
import com.bone.iam.application.support.PasswordPolicyValidator;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.AccountRole;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.AccountRoleRepository;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.iam.domain.repository.RoleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** AuthApplicationService#refreshToken 单元测试（原 RefreshTokenCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceRefreshTokenTest {

  @Mock RefreshTokenIssuer refreshTokenIssuer;
  @Mock AccountRepository accountRepository;
  @Mock AccountRoleRepository accountRoleRepository;
  @Mock RoleRepository roleRepository;
  @Mock RolePermissionRepository rolePermissionRepository;
  @Mock PermissionRepository permissionRepository;
  @Mock AccessTokenIssuer accessTokenIssuer;
  @Mock PasswordPolicyValidator passwordPolicyValidator;
  @Mock AccountAuthorityCache accountAuthorityCache;
  @Mock RoleHierarchyResolver roleHierarchyResolver;
  @Mock PasswordEncoderPort passwordEncoderPort;
  @Mock TokenBlacklistPort tokenBlacklistPort;
  @Mock JwtConfig jwtConfig;

  AuthApplicationService authApplicationService;
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void setUp() {
    authApplicationService =
        new AuthApplicationService(
            accountRepository,
            accountRoleRepository,
            roleRepository,
            rolePermissionRepository,
            permissionRepository,
            accessTokenIssuer,
            refreshTokenIssuer,
            passwordPolicyValidator,
            new IamPasswordProperties(),
            accountAuthorityCache,
            roleHierarchyResolver,
            passwordEncoderPort,
            tokenBlacklistPort,
            jwtConfig,
            org.mockito.Mockito.mock(com.bone.iam.domain.repository.TenantRepository.class),
            meterRegistry);
  }

  @Test
  void refreshTokenSuccessfully() {
    Account account =
        Account.create(1L, Username.of("bob"), "hash", Email.of("b@b.com"), null, null, 0L);

    when(refreshTokenIssuer.rotate("old-refresh"))
        .thenReturn(Map.of("accountId", "1", "tenantId", "0", "refreshToken", "new-refresh"));
    when(accountRepository.findById(1L)).thenReturn(account);
    when(accountAuthorityCache.get(1L)).thenReturn(Optional.of(List.of("iam:read")));
    when(accessTokenIssuer.issueAccessToken(1L, "bob", 0L, List.of("iam:read"), List.of()))
        .thenReturn("new-access");

    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("old-refresh");

    Map<String, String> result = authApplicationService.refreshToken(cmd);

    assertThat(meterRegistry.counter("iam_refresh_requests_total").count()).isEqualTo(1.0);

    assertThat(result.get("accessToken")).isEqualTo("new-access");
    assertThat(result.get("refreshToken")).isEqualTo("new-refresh");
    verify(refreshTokenIssuer, times(1)).rotate("old-refresh");
  }

  @Test
  void blankRefreshTokenThrows400() {
    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("  ");

    assertThatThrownBy(() -> authApplicationService.refreshToken(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("刷新令牌不能为空");
  }

  @Test
  void invalidRefreshTokenThrows401() {
    when(refreshTokenIssuer.rotate("bad")).thenThrow(new IllegalArgumentException("invalid token"));

    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("bad");

    assertThatThrownBy(() -> authApplicationService.refreshToken(cmd))
        .isInstanceOf(BizException.class);
  }

  /**
   * 刷新时按<b>继承闭包</b>重解析角色码并写入令牌（字段级角色裁剪的身份来源）。
   *
   * <p>为什么盯这条：角色码只存在于登录/刷新时刻的快照里，若刷新不同步重解析，「会话期内角色被调整」的用户会一直持有旧角色码——按新角色配的字段裁剪不生效，按旧角色收掉的字段仍然放行（fail-open
   * 窗口 = token 剩余有效期）。
   */
  @Test
  void refresh_reResolvesRoleCodesWithInheritanceClosure() {
    Account account =
        Account.create(2L, Username.of("carol"), "hash", Email.of("c@c.com"), null, null, 0L);
    AccountRole direct = AccountRole.of(500L, 0L, 2L, 10L); // 直接角色 roleId=10
    Role inherited = Role.create("员工", "employee", null, 1, 0L, null); // 经 parent_role_id 继承

    when(refreshTokenIssuer.rotate("tok"))
        .thenReturn(Map.of("accountId", "2", "tenantId", "0", "refreshToken", "new-refresh"));
    when(accountRepository.findById(2L)).thenReturn(account);
    when(accountAuthorityCache.get(2L)).thenReturn(Optional.of(List.of("iam:read")));
    when(accountRoleRepository.findByAccount(2L)).thenReturn(List.of(direct));
    // LinkedHashSet 镜像 resolveClosure 返回契约（插入序确定），避免 Set.of 无序迭代
    // 使下游 findByIds 实参顺序随机而撞 strict stubbing
    when(roleHierarchyResolver.resolveClosure(List.of(10L)))
        .thenReturn(new LinkedHashSet<>(List.of(10L, 11L)));
    when(roleRepository.findByIds(List.of(10L, 11L))).thenReturn(List.of(inherited));
    when(accessTokenIssuer.issueAccessToken(
            2L, "carol", 0L, List.of("iam:read"), List.of("employee")))
        .thenReturn("new-access");

    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("tok");

    Map<String, String> result = authApplicationService.refreshToken(cmd);

    assertThat(result.get("accessToken")).isEqualTo("new-access");
    // 继承来的角色码必须进令牌（少它 = 继承角色的字段裁剪对后端不可见）
    verify(accessTokenIssuer)
        .issueAccessToken(2L, "carol", 0L, List.of("iam:read"), List.of("employee"));
  }
}
