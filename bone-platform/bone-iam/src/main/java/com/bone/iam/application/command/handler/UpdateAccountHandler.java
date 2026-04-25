package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.UpdateAccountCmd;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateAccountHandler {
    private final AccountRepository accountRepository;

    @Transactional
    public void handle(UpdateAccountCmd cmd) {
        Account account = accountRepository.findById(cmd.getId());
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        account.updateProfile(cmd.getRealName(), cmd.getPhone(), null);
        if (cmd.getStatus() != null) {
            AccountStatus status = AccountStatus.of(cmd.getStatus());
            if (status == AccountStatus.ENABLED && account.getStatus() != AccountStatus.ENABLED) {
                account.enable();
            } else if (status == AccountStatus.DISABLED && account.getStatus() != AccountStatus.DISABLED) {
                account.disable();
            }
        }
        accountRepository.update(account);
    }
}
