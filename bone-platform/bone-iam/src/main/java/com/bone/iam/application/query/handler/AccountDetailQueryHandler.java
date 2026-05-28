package com.bone.iam.application.query.handler;

import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.mapper.AccountDtoMapper;
import com.bone.iam.application.service.AccountRoleBindingService;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountDetailQueryHandler {

    private final AccountRepository accountRepository;
    private final AccountDtoMapper accountDtoMapper;
    private final AccountRoleBindingService accountRoleBindingService;

    @Transactional(readOnly = true)
    public Optional<AccountDTO> handle(Long id) {
        return Optional.ofNullable(accountRepository.findById(id))
                // 租户隔离：非平台租户不允许查看其他租户的账号详情（防 IDOR）。详设 §3.4 / §4.8。
                .filter(account -> {
                    Long caller = TenantContext.getTenantId();
                    return caller == null || caller == 0L || caller.equals(account.getTenantId());
                })
                .map(account -> {
                    AccountDTO dto = accountDtoMapper.toDto(account);
                    dto.setRoleIds(accountRoleBindingService.listRoleIds(id).toArray(Long[]::new));
                    return dto;
                });
    }
}
