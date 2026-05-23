package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.EnableAccountCommand;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EnableAccountHandler {
    private final AccountRepository accountRepository;

    @Transactional
    public void handle(EnableAccountCommand cmd) {
        Account account = accountRepository.findById(cmd.getId());
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        account.enable();
        accountRepository.update(account);
    }
}
