package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.ResetPasswordCommand;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ResetPasswordCommandHandler {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void handle(ResetPasswordCommand cmd) {
        Account account = accountRepository.findById(cmd.getId());
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        String passwordHash = passwordEncoder.encode(cmd.getNewPassword());
        account.updatePassword(passwordHash);
        accountRepository.update(account);
    }
}
