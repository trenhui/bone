package com.bone.iam.application.query.handler;

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
        return Optional.ofNullable(accountRepository.findById(id)).map(account -> {
            AccountDTO dto = accountDtoMapper.toDto(account);
            dto.setRoleIds(accountRoleBindingService.listRoleIds(id).toArray(Long[]::new));
            return dto;
        });
    }
}
