package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.adjustment.AdjustmentLine;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/** A6 理算仓储（理算记录 + 明细行分开查询：与 blueprint 同款，集合不随聚合回填）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface AdjustmentRepository extends Repository<Adjustment, Long> {

  Adjustment findById(@Param("calculationId") long calculationId);

  @TenantScope(TenantScopeMode.AUTO)
  Adjustment findLatestByClaimId(@Param("claimId") long claimId);

  @TenantScope(TenantScopeMode.AUTO)
  List<AdjustmentLine> findLinesByCalculationId(@Param("calculationId") long calculationId);
}
