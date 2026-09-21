package com.bone.iam.application.service;

import com.bone.iam.application.port.out.PasswordEncoderPort;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.client.SsoClient;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service("applicationAuthService")
@RequiredArgsConstructor
@Slf4j
public class AuthService {
  private final AccountRepository accountRepository;
  private final PasswordEncoderPort passwordEncoder;
  private final SsoClient ssoClient;

  /**
   * 按用户名定位账号；不存在或重名返回 {@link Optional#empty()}。锁定/禁用/密码校验由调用方处理。
   *
   * <p>登录入口租户未知，查找本身跨租户（见 {@link
   * AccountRepository#findByUsernameForLoginAllTenants(String)}）；原先这里硬编码 {@code tenantId =
   * 0}，会把登录限制在默认租户、 使多租户账号永远登录不上。
   */
  public Optional<Account> findByUsername(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    try {
      return accountRepository.findByUsernameForLoginAllTenants(username);
    } catch (MultipleResultsException e) {
      // 跨租户重名：登录无法确定租户。必须留痕——静默转 empty 会让两个租户的同名账号同时登录不上且无人知晓。
      log.error(
          "[IAM_LOGIN] 按用户名定位到多个账号，无法确定租户，登录拒绝（username={}）。" + "请检查是否存在跨租户同名账号，或改为「用户名 + 租户」登录",
          username,
          e);
      return Optional.empty();
    }
  }

  /** 按用户名在<b>当前租户</b>内查重（建账号等已认证路径）。 */
  public Optional<Account> findByUsernameInTenant(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    return accountRepository.findByUsernameInTenant(username);
  }

  /** 校验明文密码是否与账号 hash 匹配。 */
  public boolean matches(String rawPassword, Account account) {
    if (account == null || rawPassword == null) {
      return false;
    }
    return passwordEncoder.matches(rawPassword, account.getPasswordHash());
  }

  /**
   * @deprecated 仅保留向后兼容；新调用方应使用 {@link #findByUsername(String)} + {@link #matches(String,
   *     Account)}， 以便登录处理器统一编排锁定/失败计数。
   */
  @Deprecated
  public Account authenticate(String username, String password) {
    return findByUsername(username).filter(a -> matches(password, a)).orElse(null);
  }

  public boolean ssoAuthenticate(String username, String password) {
    return ssoClient.authenticate(username, password);
  }
}
