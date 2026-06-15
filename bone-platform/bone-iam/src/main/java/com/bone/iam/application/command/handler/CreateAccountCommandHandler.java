package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.CreateAccountCommand;
import com.bone.iam.application.service.AccountRoleBindingService;
import com.bone.iam.application.service.AuthService;
import com.bone.iam.application.service.PasswordPolicyValidator;
import com.bone.iam.application.service.TenantQuotaEnforcer;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.Email;
import com.bone.iam.domain.account.vo.Username;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateAccountCommandHandler {
  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccountRoleBindingService accountRoleBindingService;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final TenantQuotaEnforcer tenantQuotaEnforcer;
  private final AuthService authService;

  @Transactional
  public Long handle(CreateAccountCommand cmd) {
    passwordPolicyValidator.assertAcceptable(cmd.getPassword());
    long tenantId = cmd.getTenantId() != null ? cmd.getTenantId() : 0L;
    tenantQuotaEnforcer.assertCanAddAccount(tenantId);
    Username username = Username.of(cmd.getUsername());
    Email email = Email.of(cmd.getEmail());

    // 检查用户名是否已存在
    if (authService.findByUsername(username.value()).isPresent()) {
      throw new com.bone.core.exception.BizException(409, "用户名已存在: " + cmd.getUsername());
    }

    String passwordHash = passwordEncoder.encode(cmd.getPassword());

    // id 由数据库 AUTO_INCREMENT 生成，metadata-sdk insert 后会回填
    Account account =
        Account.create(
            null, username, passwordHash, email, cmd.getPhone(), cmd.getRealName(), tenantId);
    accountRepository.save(account);
    accountRoleBindingService.replaceBindings(
        account.getId(), account.getTenantId(), cmd.getRoleIds());
    return account.getId();
  }
}
