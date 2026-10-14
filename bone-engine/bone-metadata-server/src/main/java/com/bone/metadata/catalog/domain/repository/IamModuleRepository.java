package com.bone.metadata.catalog.domain.repository;

import com.bone.metadata.catalog.domain.model.iam.IamModuleRef;
import com.bone.metadata.sdk.Repository;
import java.util.List;

/** 只读仓储：查询 IAM 独占的 {@code bone_module} 表，供 metadata 侧校验模块存在性。 仅使用读取能力，不暴露写操作。 */
public interface IamModuleRepository extends Repository<IamModuleRef, Long> {

  /**
   * 租户全量模块读模型（建模工作台聚合端点用，一次取全避免 N 个应用 N 次查询）。
   *
   * <p>显式过滤 tenantId 与 pageEntities 同口径：SDK 逻辑删除不自动过滤 deleted， 且读侧不得依赖隐式租户注入（IamModuleValidator
   * 的显式契约原则）。
   */
  default List<IamModuleRef> listByTenant(long tenantId) {
    return query().where(IamModuleRef::getTenantId).eq(tenantId).list();
  }
}
