package com.bone.iam.domain.repository;

import com.bone.iam.domain.account.Account;
import com.bone.metadata.sdk.Repository;

import java.util.Optional;

public interface AccountRepository extends Repository<Account, Long> {
    Optional<Account> findByUsername(String username);
}
