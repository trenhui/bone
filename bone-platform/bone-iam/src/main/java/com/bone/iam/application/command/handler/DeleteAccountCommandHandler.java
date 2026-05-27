package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.application.service.AccountRoleBindingService;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "DeleteAccount",
        description = "删除账号（逻辑删除）",
        inputSchema = "{\"id\": \"long\"}",
        outputSchema = "{\"success\": \"boolean\"}",
        idempotent = true,
        cost = 1,
        retryable = true,
        timeout = 5
)
@Component
@RequiredArgsConstructor
public class DeleteAccountCommandHandler {
    private final AccountRepository accountRepository;
    private final AccountRoleBindingService accountRoleBindingService;

    @Transactional
    public void handle(Long id) {
        accountRoleBindingService.replaceBindings(id, null, new Long[0]);
        accountRepository.deleteById(id);
    }
}

