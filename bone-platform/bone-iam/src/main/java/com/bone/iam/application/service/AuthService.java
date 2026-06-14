package com.bone.iam.application.service;

import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.client.SsoClient;
import com.bone.iam.domain.gateway.PasswordEncoderPort;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationAuthService")
@RequiredArgsConstructor
public class AuthService {
  private final AccountRepository accountRepository;
  private final PasswordEncoderPort passwordEncoder;
  private final SsoClient ssoClient;

  /** 按用户名定位账号；不存在或重名返回 {@link Optional#empty()}。锁定/禁用/密码校验由调用方处理。 */
  public Optional<Account> findByUsername(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    try {
      // 查询时添加租户条件（默认租户ID为0）
      Account account =
          accountRepository.findOneByCriteria(
              Criteria.<Account>create().eq("username", username).eq("tenantId", 0L));
      return Optional.ofNullable(account);
    } catch (MultipleResultsException e) {
      return Optional.empty();
    }
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
