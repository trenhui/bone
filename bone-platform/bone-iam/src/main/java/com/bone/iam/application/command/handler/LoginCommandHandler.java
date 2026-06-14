package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.LoginCommand;
import com.bone.iam.application.config.IamPasswordProperties;
import com.bone.iam.application.query.handler.AccountAuthoritiesQueryHandler;
import com.bone.iam.application.service.AuthService;
import com.bone.iam.application.service.PasswordPolicyValidator;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.repository.AccountRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录编排：解析账号 → 检查锁定/禁用 → 校验密码 → 失败计数 / 成功清零 → 颁发 access + refresh token。
 *
 * <p>账号锁定与计数依赖 {@link Account#recordLoginFailure()}，阈值由 {@link IamPasswordProperties}（默认 5 次/30
 * 分钟）声明，对齐详设 §7.1 / IAM-19。
 */
@Capability(
    name = "Login",
    description = "账号登录",
    inputSchema = "{\"username\": \"string\", \"password\": \"string\"}",
    outputSchema = "{\"token\": \"string\", \"account\": {}}",
    idempotent = false,
    cost = 1,
    retryable = false,
    timeout = 5)
@Component
@RequiredArgsConstructor
public class LoginCommandHandler {
  private final AuthService authService;
  private final AccountRepository accountRepository;
  private final AccessTokenIssuer accessTokenIssuer;
  private final RefreshTokenIssuer refreshTokenIssuer;
  private final AccountAuthoritiesQueryHandler accountAuthoritiesQueryHandler;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final IamPasswordProperties passwordProperties;

  @Transactional
  public Map<String, Object> handle(LoginCommand cmd) {
    Account account =
        authService
            .findByUsername(cmd.getUsername())
            .orElseThrow(() -> BizException.of(401, IamErrorCodes.LOGIN_FAILED + ": 用户名或密码错误"));

    if (account.getStatus() == AccountStatus.DISABLED) {
      throw BizException.of(403, IamErrorCodes.ACCOUNT_DISABLED + ": 账号已禁用，请联系管理员");
    }
    if (account.isLocked()) {
      long remainingSec =
          account.getLockedAt() == null
              ? 0
              : Math.max(
                  0, Duration.between(LocalDateTime.now(), account.getLockedAt()).getSeconds());
      throw BizException.of(
          423, IamErrorCodes.ACCOUNT_LOCKED + ": 账号已锁定，剩余 " + remainingSec + " 秒");
    }

    if (!authService.matches(cmd.getPassword(), account)) {
      account.recordLoginFailure(
          passwordProperties.getLockoutThreshold(), passwordProperties.getLockoutMinutes());
      accountRepository.update(account);
      throw BizException.of(401, IamErrorCodes.LOGIN_FAILED + ": 用户名或密码错误");
    }

    account.recordLoginSuccess(cmd.getClientIp());
    accountRepository.update(account);

    List<String> scopes =
        accountAuthoritiesQueryHandler.resolvePermissionCodes(account.getId(), account.isAdmin());
    String token =
        accessTokenIssuer.issueAccessToken(
            account.getId(), account.getUsername().value(), account.getTenantId(), scopes);
    String refreshToken = refreshTokenIssuer.issue(account.getId(), account.getTenantId());

    boolean weak = passwordPolicyValidator.requiresPasswordChange(cmd.getPassword());
    boolean expired = isPasswordExpired(account);

    Map<String, Object> result = new HashMap<>();
    result.put("token", token);
    result.put("refreshToken", refreshToken);
    result.put("account", account);
    result.put("requirePasswordChange", weak || expired);
    return result;
  }

  private boolean isPasswordExpired(Account account) {
    int maxAge = passwordProperties.getMaxAgeDays();
    if (maxAge <= 0 || account.getPasswordUpdatedAt() == null) {
      return false;
    }
    return account.getPasswordUpdatedAt().plusDays(maxAge).isBefore(LocalDateTime.now());
  }
}
