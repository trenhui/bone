package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.UpdateMyProfileCommand;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 自助更新本账号 profile；不允许跨账号操作。 */
@Component
@RequiredArgsConstructor
public class UpdateMyProfileCommandHandler {

  private final AccountRepository accountRepository;

  @Transactional
  public void handle(UpdateMyProfileCommand cmd) {
    if (cmd.getAccountId() == null) {
      throw BizException.of(401, IamErrorCodes.PROFILE_OWNERSHIP_DENIED);
    }
    Account account = accountRepository.findById(cmd.getAccountId());
    if (account == null) {
      throw BizException.of(404, IamErrorCodes.PROFILE_OWNERSHIP_DENIED);
    }
    account.updateProfile(cmd.getRealName(), cmd.getPhone(), cmd.getAvatarUrl());
    accountRepository.update(account);
  }
}
