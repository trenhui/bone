package com.bone.metadata.catalog.domain.repository;

import com.bone.metadata.catalog.domain.model.iam.IamApplicationRef;
import com.bone.metadata.sdk.Repository;
import java.util.List;

/** 只读仓储：查询 IAM 独占的 {@code bone_application} 表，供 metadata 侧校验应用存在性。 仅使用读取能力，不暴露写操作。 */
public interface IamApplicationRepository extends Repository<IamApplicationRef, Long> {

  /** 租户全量应用读模型（建模工作台聚合端点用；应用名仅为模块分组的展示标签，真源在 IAM）。 */
  default List<IamApplicationRef> listByTenant(long tenantId) {
    return query().where(IamApplicationRef::getTenantId).eq(tenantId).list();
  }
}
