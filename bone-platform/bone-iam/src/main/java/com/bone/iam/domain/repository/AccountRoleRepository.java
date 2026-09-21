package com.bone.iam.domain.repository;

import com.bone.iam.domain.account.AccountRole;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 账号-角色绑定聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>绑定关系读取属「本聚合读」：DSL 驻留此处，{@code application} 层只调方法（E-4.2）。
 */
public interface AccountRoleRepository extends Repository<AccountRole, Long> {

  /** 某账号的全部角色绑定。 */
  default List<AccountRole> findByAccount(Long accountId) {
    if (accountId == null) {
      return List.of();
    }
    return findByCriteria(Criteria.<AccountRole>create().eq("accountId", accountId));
  }

  /** 重建某账号的全部角色绑定（物理删除后插入，表无软删列）。application 层只调本方法（E-4.2）。 */
  default void replaceBindingsForAccount(Long accountId, Long tenantId, Long[] roleIds) {
    if (accountId == null) {
      return;
    }
    long safeTenant = tenantId != null ? tenantId : 0L;
    deleteByCriteria(Criteria.<AccountRole>create().eq("accountId", accountId));
    if (roleIds != null && roleIds.length > 0) {
      List<AccountRole> links =
          Arrays.stream(roleIds)
              .filter(Objects::nonNull)
              .distinct()
              .map(roleId -> AccountRole.of(null, safeTenant, accountId, roleId))
              .toList();
      if (!links.isEmpty()) {
        batchInsert(links);
      }
    }
  }
}
