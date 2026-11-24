package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.liability.FactorMatrix;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 理算责任因子矩阵仓储（一行一格；按责任+因子组合+版本整体读写）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface FactorMatrixRepository extends Repository<FactorMatrix, Long> {

  /**
   * 责任 × 因子组合 × 版本的全部格子（含 DRAFT/PUBLISHED；内存组装由 application 完成）。
   *
   * <p>{@code dimensionSet} 为空时不加该条件——GET 只给 liabilityId/version 时可一次取回该责任该版本的所有因子组合。
   */
  default List<FactorMatrix> findByLiabilityAndVersion(
      Long tenantId, Long liabilityId, String dimensionSet, Integer version) {
    return findByCriteria(
        Criteria.<FactorMatrix>create()
            .eq(FactorMatrix::getTenantId, tenantId)
            .eq(FactorMatrix::getLiabilityId, liabilityId)
            .eq(
                dimensionSet != null && !dimensionSet.isBlank(),
                FactorMatrix::getDimensionSet,
                dimensionSet)
            .eq(FactorMatrix::getVersion, version)
            .orderByAsc(FactorMatrix::getDimensionSet)
            .orderByAsc(FactorMatrix::getRowKey)
            .orderByAsc(FactorMatrix::getColKey));
  }

  /**
   * 某责任全部已发布的矩阵行（不限版本/因子组合），供Java侧选出生效版本。
   *
   * <p>不在SQL里做 MAX(version)：SDK Criteria 不暴露聚合，且"取哪个版本"是口径（PUBLISHED 中最大 version），放到应用层显式表达更可测。
   * 无已发布行返回空列表——调用方据此回落责任统一比例，零行为变更。
   */
  default List<FactorMatrix> findPublishedByLiability(Long tenantId, Long liabilityId) {
    return findByCriteria(
        Criteria.<FactorMatrix>create()
            .eq(FactorMatrix::getTenantId, tenantId)
            .eq(FactorMatrix::getLiabilityId, liabilityId)
            .eq(FactorMatrix::getStatus, FactorMatrix.STATUS_PUBLISHED)
            .orderByDesc(FactorMatrix::getVersion));
  }
}
