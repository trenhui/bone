package com.bone.metadata.catalog.domain.repository;

import com.bone.metadata.catalog.domain.model.iam.IamAppRoleRef;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Optional;

/** 只读仓储：查询 IAM 独占的 {@code bone_app_permission} 表（G1② 应用角色校验）。 仅使用读取能力，不暴露写操作。 */
public interface IamAppRoleRepository extends Repository<IamAppRoleRef, Long> {

  /** 按 (应用 ID, 用户 ID) 定位唯一角色绑定（唯一键 {@code uk_app_user} 保证至多一条）。 */
  default Optional<IamAppRoleRef> findByAppAndUser(Long appId, Long userId) {
    return Optional.ofNullable(
        findOneByCriteria(
            Criteria.<IamAppRoleRef>create().eq("appId", appId).eq("userId", userId)));
  }
}
