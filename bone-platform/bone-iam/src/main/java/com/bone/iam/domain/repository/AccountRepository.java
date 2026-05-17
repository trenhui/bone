package com.bone.iam.domain.repository;

import com.bone.iam.domain.account.Account;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Sql;

public interface AccountRepository extends Repository<Account, Long> {

    @Sql("""
        SELECT * FROM iam_account
        WHERE username = #{username} AND deleted = 0
        LIMIT 1
        """)
    Account findByUsername(String username);
}
