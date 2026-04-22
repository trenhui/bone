package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.model.role.vo.RoleId;
import com.bone.metadata.sdk.Repository;

import java.util.Optional;

public interface RoleRepository extends Repository<Role, RoleId> {
    Optional<Role> findByName(String name);
    boolean existsByName(String name);
}