package com.bone.iam.domain.repository;

import com.bone.iam.domain.role.Role;
import com.bone.metadata.sdk.Repository;

public interface RoleRepository extends Repository<Role, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}