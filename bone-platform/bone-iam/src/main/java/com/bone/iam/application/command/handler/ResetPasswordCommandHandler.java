package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.ResetPasswordCommand;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.service.PasswordPolicyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ResetPasswordCommandHandler {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @Transactional
    public void handle(ResetPasswordCommand cmd) {
        passwordPolicyValidator.assertAcceptable(cmd.getNewPassword());
        Account account = accountRepository.findById(cmd.getId());
        if (account == null) {
            throw new RuntimeException("账户不存在");
        }
        String passwordHash = passwordEncoder.encode(cmd.getNewPassword());
        account.updatePassword(passwordHash);
        accountRepository.update(account);
    }
}
