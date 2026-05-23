package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.qry.AccountPageQuery;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AccountPageQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<AccountDTO> handle(AccountPageQuery qry) {
        FluentQuery<Account> query = QueryBuilder.from(Account.class);

        if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
            query.where(Account::getUsername).like(qry.getKeyword())
                 .or(Account::getEmail).like(qry.getKeyword())
                 .or(Account::getRealName).like(qry.getKeyword());
        }

        if (qry.getStatus() != null) {
            query.where(Account::getStatus).eq(AccountStatus.of(qry.getStatus()));
        }

        if (qry.getTenantId() != null) {
            query.where(Account::getTenantId).eq(qry.getTenantId());
        }

        PageResult<Account> result = query.orderByDesc(Account::getCreatedAt)
                                          .page(qry.getPage(), qry.getSize());

        List<AccountDTO> dtoList = result.getRecords().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    private AccountDTO convertToDto(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername().value());
        dto.setEmail(account.getEmail().value());
        dto.setPhone(account.getPhone());
        dto.setRealName(account.getRealName());
        dto.setAvatarUrl(account.getAvatarUrl());
        dto.setStatus(account.getStatus() != null ? account.getStatus().getCode() : null);
        dto.setIsAdmin(account.isAdmin());
        dto.setTenantId(account.getTenantId());
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setLastLoginIp(account.getLastLoginIp());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        return dto;
    }
}
