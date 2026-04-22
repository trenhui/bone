package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.permission.Permission;
import com.bone.metadata.sdk.Repository;

public interface PermissionRepository extends Repository<Permission, Long> {
    Permission findByCode(String code);
    boolean existsByCode(String code);
}