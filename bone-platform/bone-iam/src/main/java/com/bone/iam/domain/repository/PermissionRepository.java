package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.permission.vo.PermissionId;
import com.bone.metadata.sdk.Repository;

public interface PermissionRepository extends Repository<Permission, PermissionId> {
    Permission findByCode(String code);
    boolean existsByCode(String code);
}