package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.command.cmd.DisableAccountCommand;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DisableAccountCommandHandler {
  private final AccountRepository accountRepository;

  @Transactional
  public void handle(DisableAccountCommand cmd) {
    Account account = accountRepository.findById(cmd.getId());
    if (account == null) {
      throw NotFoundException.of("账户不存在");
    }
    try {
      account.disable();
    } catch (IllegalStateException ex) {
      throw BizException.of(409, ex.getMessage());
    }
    accountRepository.update(account);
  }
}
