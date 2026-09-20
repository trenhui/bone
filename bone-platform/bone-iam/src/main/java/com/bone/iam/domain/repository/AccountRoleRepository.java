package com.bone.iam.domain.repository;

import com.bone.iam.domain.account.AccountRole;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

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
}
