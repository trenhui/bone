package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.CreateAccountCommand;
import com.bone.iam.application.service.AccountRoleBindingService;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.Email;
import com.bone.iam.domain.account.vo.Username;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateAccountHandler {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRoleBindingService accountRoleBindingService;

    @Transactional
    public Long handle(CreateAccountCommand cmd) {
        Long accountId = DistributedIdGenerator.generateLongId();
        Username username = Username.of(cmd.getUsername());
        Email email = Email.of(cmd.getEmail());
        String passwordHash = passwordEncoder.encode(cmd.getPassword());

        Account account = Account.create(accountId, username, passwordHash, email,
                cmd.getPhone(), cmd.getRealName(), cmd.getTenantId());
        accountRepository.save(account);
        accountRoleBindingService.replaceBindings(
                account.getId(), account.getTenantId(), cmd.getRoleIds());
        return account.getId();
    }
}
