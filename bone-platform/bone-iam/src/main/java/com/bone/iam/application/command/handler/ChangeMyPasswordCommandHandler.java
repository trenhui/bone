package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.ChangeMyPasswordCommand;
import com.bone.iam.application.service.PasswordPolicyValidator;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自助改密：校验旧密码 → 应用弱口令策略 → 重哈希 → 更新 {@code password_updated_at}。
 *
 * <p>注意：不会自动吊销 refresh token；如需"改密即下线"，前端应在改密成功后调用 {@code DELETE /accounts/{id}/sessions}（需 {@code
 * iam:sessions:write} 权限）或登出。
 */
@Component
@RequiredArgsConstructor
public class ChangeMyPasswordCommandHandler {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;

  @Transactional
  public void handle(ChangeMyPasswordCommand cmd) {
    if (cmd.getAccountId() == null) {
      throw BizException.of(401, IamErrorCodes.PROFILE_OWNERSHIP_DENIED);
    }
    Account account = accountRepository.findById(cmd.getAccountId());
    if (account == null) {
      throw BizException.of(404, IamErrorCodes.PROFILE_OWNERSHIP_DENIED);
    }
    if (cmd.getOldPassword() == null
        || !passwordEncoder.matches(cmd.getOldPassword(), account.getPasswordHash())) {
      throw BizException.of(401, IamErrorCodes.OLD_PASSWORD_MISMATCH + ": 原密码不正确");
    }
    passwordPolicyValidator.assertAcceptable(cmd.getNewPassword());
    account.updatePassword(passwordEncoder.encode(cmd.getNewPassword()));
    accountRepository.update(account);
  }
}
