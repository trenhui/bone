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
import com.bone.iam.application.policy.PasswordPolicyValidator;
import com.bone.iam.application.port.out.PasswordEncoderPort;
import com.bone.iam.application.port.out.TokenBlacklistPort;
import com.bone.iam.domain.client.SsoClient;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.AccountRoleRepository;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.repository.RolePermissionRepository;
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
  @Mock RolePermissionRepository rolePermissionRepository;
  @Mock PermissionRepository permissionRepository;
  @Mock AccessTokenIssuer accessTokenIssuer;
  @Mock PasswordPolicyValidator passwordPolicyValidator;
  @Mock AccountAuthorityCache accountAuthorityCache;
  @Mock RoleHierarchyResolver roleHierarchyResolver;
  @Mock PasswordEncoderPort passwordEncoderPort;
  @Mock SsoClient ssoClient;
  @Mock TokenBlacklistPort tokenBlacklistPort;
  @Mock JwtConfig jwtConfig;

  AuthApplicationService authApplicationService;

  @BeforeEach
  void setUp() {
    authApplicationService =
        new AuthApplicationService(
            accountRepository,
            accountRoleRepository,
            rolePermissionRepository,
            permissionRepository,
            accessTokenIssuer,
            refreshTokenIssuer,
            passwordPolicyValidator,
            new IamPasswordProperties(),
            accountAuthorityCache,
            roleHierarchyResolver,
            passwordEncoderPort,
            ssoClient,
            tokenBlacklistPort,
            jwtConfig);
  }

  @Test
  void refreshTokenSuccessfully() {
    Account account =
        Account.create(1L, Username.of("bob"), "hash", Email.of("b@b.com"), null, null, 0L);

    when(refreshTokenIssuer.rotate("old-refresh"))
        .thenReturn(Map.of("accountId", "1", "tenantId", "0", "refreshToken", "new-refresh"));
    when(accountRepository.findById(1L)).thenReturn(account);
    when(accountAuthorityCache.get(1L)).thenReturn(Optional.of(List.of("iam:read")));
    when(accessTokenIssuer.issueAccessToken(1L, "bob", 0L, List.of("iam:read")))
        .thenReturn("new-access");

    RefreshTokenCommand cmd = new RefreshTokenCommand();
    cmd.setRefreshToken("old-refresh");

    Map<String, String> result = authApplicationService.refreshToken(cmd);

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
}
