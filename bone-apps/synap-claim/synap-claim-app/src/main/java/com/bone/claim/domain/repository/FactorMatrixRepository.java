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
}
