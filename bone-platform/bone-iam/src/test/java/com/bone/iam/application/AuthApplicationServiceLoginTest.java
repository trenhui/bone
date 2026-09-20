package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.LoginCommand;
import com.bone.iam.application.config.IamPasswordProperties;
import com.bone.iam.application.service.AuthService;
import com.bone.iam.application.service.PasswordPolicyValidator;
import com.bone.iam.application.service.RoleHierarchyResolver;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.iam.domain.account.vo.Email;
import com.bone.iam.domain.account.vo.Username;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.AccountRoleRepository;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.repository.RolePermissionRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 登录失败锁定 / 密码到期 / 成功清零 三条主路径的纯单测（不拉起 Spring）。 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceLoginTest {

  @Mock AuthService authService;

  @Mock AccountRepository accountRepository;

  @Mock AccountRoleRepository accountRoleRepository;

  @Mock RolePermissionRepository rolePermissionRepository;

  @Mock PermissionRepository permissionRepository;

  @Mock AccessTokenIssuer accessTokenIssuer;

  @Mock RefreshTokenIssuer refreshTokenIssuer;

  @Mock PasswordPolicyValidator passwordPolicyValidator;

  @Mock AccountAuthorityCache accountAuthorityCache;

  @Mock RoleHierarchyResolver roleHierarchyResolver;

  IamPasswordProperties passwordProperties;

  AuthApplicationService authApplicationService;

  @BeforeEach
  void setUp() {
    passwordProperties = new IamPasswordProperties();
    passwordProperties.setLockoutThreshold(3);
    passwordProperties.setLockoutMinutes(15);
    passwordProperties.setMaxAgeDays(90);
    authApplicationService =
        new AuthApplicationService(
            authService,
            accountRepository,
            accountRoleRepository,
            rolePermissionRepository,
            permissionRepository,
            accessTokenIssuer,
            refreshTokenIssuer,
            passwordPolicyValidator,
            passwordProperties,
            accountAuthorityCache,
            roleHierarchyResolver);
  }

  @Test
  void successfulLoginIssuesTokensAndClearsFailures() {
    Account account = mkAccount();
    setField(account, "loginFailCount", 2);
    when(authService.findByUsername("alice")).thenReturn(Optional.of(account));
    when(authService.matches("good-pass", account)).thenReturn(true);
    when(accountAuthorityCache.get(anyLong()))
        .thenReturn(Optional.of(List.of("iam:accounts:read")));
    when(accessTokenIssuer.issueAccessToken(anyLong(), any(), any(), any())).thenReturn("ACCESS");
    when(refreshTokenIssuer.issue(anyLong(), any())).thenReturn("REFRESH");
    when(passwordPolicyValidator.requiresPasswordChange("good-pass")).thenReturn(false);

    LoginCommand cmd = new LoginCommand();
    cmd.setUsername("alice");
    cmd.setPassword("good-pass");
    cmd.setClientIp("10.0.0.1");

    Map<String, Object> result = authApplicationService.login(cmd);

    assertThat(result.get("token")).isEqualTo("ACCESS");
    assertThat(result.get("refreshToken")).isEqualTo("REFRESH");
    assertThat(result.get("requirePasswordChange")).isEqualTo(false);
    verify(accountRepository, times(1)).update(account);
    assertThat(account.getLoginFailCount()).isZero();
    assertThat(account.getLastLoginIp()).isEqualTo("10.0.0.1");
  }

  @Test
  void unknownUsernameThrowsLoginFailed() {
    when(authService.findByUsername("ghost")).thenReturn(Optional.empty());
    LoginCommand cmd = new LoginCommand();
    cmd.setUsername("ghost");
    cmd.setPassword("x");

    assertThatThrownBy(() -> authApplicationService.login(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(IamErrorCodes.LOGIN_FAILED);
    verify(accountRepository, never()).update(any());
  }

  @Test
  void wrongPasswordIncrementsFailureAndPersists() {
    Account account = mkAccount();
    when(authService.findByUsername("alice")).thenReturn(Optional.of(account));
    when(authService.matches("bad", account)).thenReturn(false);

    LoginCommand cmd = new LoginCommand();
    cmd.setUsername("alice");
    cmd.setPassword("bad");

    assertThatThrownBy(() -> authApplicationService.login(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(IamErrorCodes.LOGIN_FAILED);
    verify(accountRepository, times(1)).update(account);
    assertThat(account.getLoginFailCount()).isEqualTo(1);
  }

  @Test
  void exceedingThresholdLocksAccount() {
    Account account = mkAccount();
    setField(account, "loginFailCount", 2);
    when(authService.findByUsername("alice")).thenReturn(Optional.of(account));
    when(authService.matches("bad", account)).thenReturn(false);

    LoginCommand cmd = new LoginCommand();
    cmd.setUsername("alice");
    cmd.setPassword("bad");

    assertThatThrownBy(() -> authApplicationService.login(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(IamErrorCodes.LOGIN_FAILED);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
    assertThat(account.getLockedAt()).isAfter(LocalDateTime.now());
  }

  @Test
  void lockedAccountRefusesEvenWithCorrectPassword() {
    Account account = mkAccount();
    setField(account, "loginFailCount", 5);
    setField(account, "status", AccountStatus.LOCKED);
    setField(account, "lockedAt", LocalDateTime.now().plusMinutes(10));
    when(authService.findByUsername("alice")).thenReturn(Optional.of(account));

    LoginCommand cmd = new LoginCommand();
    cmd.setUsername("alice");
    cmd.setPassword("good");

    assertThatThrownBy(() -> authApplicationService.login(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(IamErrorCodes.ACCOUNT_LOCKED);
    verify(authService, never()).matches(any(), any());
  }

  @Test
  void disabledAccountRefused() {
    Account account = mkAccount();
    setField(account, "status", AccountStatus.DISABLED);
    when(authService.findByUsername("alice")).thenReturn(Optional.of(account));

    LoginCommand cmd = new LoginCommand();
    cmd.setUsername("alice");
    cmd.setPassword("good");

    assertThatThrownBy(() -> authApplicationService.login(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(IamErrorCodes.ACCOUNT_DISABLED);
  }

  @Test
  void expiredPasswordFlagsRequireChange() {
    Account account = mkAccount();
    setField(account, "passwordUpdatedAt", LocalDateTime.now().minusDays(120));
    when(authService.findByUsername("alice")).thenReturn(Optional.of(account));
    when(authService.matches("good-pass", account)).thenReturn(true);
    when(accountAuthorityCache.get(anyLong())).thenReturn(Optional.of(List.of()));
    when(accessTokenIssuer.issueAccessToken(anyLong(), any(), any(), any())).thenReturn("ACCESS");
    when(refreshTokenIssuer.issue(anyLong(), any())).thenReturn("REFRESH");
    when(passwordPolicyValidator.requiresPasswordChange("good-pass")).thenReturn(false);

    LoginCommand cmd = new LoginCommand();
    cmd.setUsername("alice");
    cmd.setPassword("good-pass");

    Map<String, Object> result = authApplicationService.login(cmd);
    assertThat(result.get("requirePasswordChange")).isEqualTo(true);
  }

  private static Account mkAccount() {
    Account account =
        Account.create(
            10L, Username.of("alice"), "hash", Email.of("a@b.com"), "13800000000", "Alice", 1L);
    setField(account, "id", 10L);
    return account;
  }

  private static void setField(Object obj, String name, Object value) {
    try {
      Field f = findField(obj.getClass(), name);
      f.setAccessible(true);
      f.set(obj, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
    Class<?> c = cls;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
