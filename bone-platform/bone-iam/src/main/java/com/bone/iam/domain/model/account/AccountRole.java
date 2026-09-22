package com.bone.iam.domain.model.account;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 账户-角色关联（表 {@code iam_account_role}）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_account_role")
public class AccountRole extends Entity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long tenantId;
  private Long accountId;
  private Long roleId;

  public static AccountRole of(Long id, Long tenantId, Long accountId, Long roleId) {
    AccountRole link = new AccountRole();
    link.id = id;
    link.tenantId = tenantId != null ? tenantId : 0L;
    link.accountId = accountId;
    link.roleId = roleId;
    return link;
  }
}
