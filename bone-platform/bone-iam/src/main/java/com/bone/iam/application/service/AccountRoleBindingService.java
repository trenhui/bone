package com.bone.iam.application.service;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.iam.domain.account.AccountRole;
import com.bone.iam.domain.repository.AccountRoleRepository;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护 {@code iam_account_role} 绑定（物理删除后重建，表无软删列）。 */
@Service
@RequiredArgsConstructor
public class AccountRoleBindingService {

    private final AccountRoleRepository accountRoleRepository;
    private final AccountAuthorityCache accountAuthorityCache;

    @Transactional
    public void replaceBindings(Long accountId, Long tenantId, Long[] roleIds) {
        if (accountId == null) {
            return;
        }
        long safeTenant = tenantId != null ? tenantId : 0L;
        accountRoleRepository
                .getSqlExecutor()
                .delete("DELETE FROM iam_account_role WHERE account_id = ?", accountId);

        if (roleIds != null && roleIds.length > 0) {
            List<AccountRole> links = Arrays.stream(roleIds)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(roleId -> AccountRole.of(
                            DistributedIdGenerator.generateLongId(), safeTenant, accountId, roleId))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!links.isEmpty()) {
                accountRoleRepository.batchInsert(links);
            }
        }
        accountAuthorityCache.evictAccount(accountId);
    }

    @Transactional(readOnly = true)
    public List<Long> listRoleIds(Long accountId) {
        if (accountId == null) {
            return List.of();
        }
        return QueryBuilder.from(AccountRole.class)
                .where(AccountRole::getAccountId)
                .eq(accountId)
                .list()
                .stream()
                .map(AccountRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
