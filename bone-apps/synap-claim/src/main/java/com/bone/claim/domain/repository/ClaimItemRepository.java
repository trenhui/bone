package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 费用明细仓储（初审核定回填合理费用专用）。
 *
 * <p>明细是 {@link ClaimCase} 聚合内实体，但聚合 {@code findById} 不回填明细（读侧走投影）， 初审核定金额必须按明细分摊落 {@code
 * valid_amount}——理算基准（A6）只认明细级合理费用， 案件级 {@code approved_amount} 不参与计算。故单独开仓储承载按案件取明细 + 逐行更新。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimItemRepository extends Repository<ClaimItem, Long> {

  @TenantScope(TenantScopeMode.AUTO)
  List<ClaimItem> findByClaimId(@Param("claimId") long claimId);
}
