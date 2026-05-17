package com.bone.iam.application.query.handler;

import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.mapper.AccountDtoMapper;
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

    @Transactional(readOnly = true)
    public Optional<AccountDTO> handle(Long id) {
        return Optional.ofNullable(accountRepository.findById(id)).map(accountDtoMapper::toDto);
    }
}
