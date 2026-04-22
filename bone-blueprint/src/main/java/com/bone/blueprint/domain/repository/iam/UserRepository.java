package com.bone.blueprint.domain.repository.iam;

import com.bone.blueprint.domain.model.iam.User;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface UserRepository extends Repository<User, Long> {
    boolean existsByUsernameAndTenantId(String username, Long tenantId);
    List<User> findByTenantId(Long tenantId);
    User findByUsername(String username);
}
