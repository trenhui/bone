package com.bone.iam.application.binding;

import com.bone.iam.domain.account.AccountRole;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.repository.AccountRoleRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护 {@code iam_account_role} 绑定：委托域仓储物理删除后重建（表无软删列），并驱逐权限缓存。 */
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
    accountRoleRepository.replaceBindingsForAccount(accountId, tenantId, roleIds);
    accountAuthorityCache.evictAccount(accountId);
  }

  @Transactional(readOnly = true)
  public List<Long> listRoleIds(Long accountId) {
    if (accountId == null) {
      return List.of();
    }
    return accountRoleRepository.findByAccount(accountId).stream()
        .map(AccountRole::getRoleId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }
}
