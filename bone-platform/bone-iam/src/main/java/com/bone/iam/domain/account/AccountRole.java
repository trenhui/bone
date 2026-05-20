package com.bone.iam.domain.account;

import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 账户-角色关联（表 {@code iam_account_role}）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_account_role")
public class AccountRole extends Entity<Long> {

    private Long id;
    private Long tenantId;
    private Long accountId;
    private Long roleId;
}
