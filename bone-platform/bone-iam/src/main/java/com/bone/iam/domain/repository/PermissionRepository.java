package com.bone.iam.domain.repository;

import com.bone.iam.domain.permission.Permission;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface PermissionRepository extends Repository<Permission, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供

    /**
     * 根据角色ID查询权限列表
     */
    List<Permission> findByRoleId(Long roleId);
}