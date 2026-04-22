package com.bone.blueprint.domain.repository.iam;

import com.bone.blueprint.domain.model.iam.Role;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface RoleRepository extends Repository<Role, Long> {
    boolean existsByNameAndTenantId(String name, Long tenantId);
    List<Role> findByTenantId(Long tenantId);
}
