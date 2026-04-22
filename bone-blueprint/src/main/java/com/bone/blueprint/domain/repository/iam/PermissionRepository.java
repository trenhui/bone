package com.bone.blueprint.domain.repository.iam;

import com.bone.blueprint.domain.model.iam.Permission;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface PermissionRepository extends Repository<Permission, Long> {
    boolean existsByCode(String code);
    List<Permission> findByParentId(Long parentId);
    List<Permission> findByType(String type);
}
