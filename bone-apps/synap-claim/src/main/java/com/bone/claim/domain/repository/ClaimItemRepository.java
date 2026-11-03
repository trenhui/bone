package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 费用明细仓储（初审核定回填合理费用 / 收单双码回填专用）。
 *
 * <p>明细是 {@link ClaimCase} 聚合内实体，但聚合 {@code findById} 不回填明细（读侧走投影）， 初审核定金额必须按明细分摊落 {@code
 * valid_amount}——理算基准（A6）只认明细级合理费用， 案件级 {@code approved_amount} 不参与计算。故单独开仓储承载按案件取明细 + 逐行更新。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimItemRepository extends Repository<ClaimItem, Long> {

  @TenantScope(TenantScopeMode.AUTO)
  List<ClaimItem> findByClaimId(@Param("claimId") long claimId);

  /**
   * 待重解析明细：指定原始费用类别码、标准码尚未回填的行（映射解决后重扫的输入）。
   *
   * <p>条件 {@code std_fee_code = ''} 是幂等的关键：已解析行（含历史上用旧映射解析的行） 不会被重扫覆盖——重复解析不重复落账（主设计 §14
   * 判据⑨）。映射纠错后想让旧行换新值， 必须走显式重放（按 mapping_version 重建），不走这条隐式路径——否则任何一次映射变更 都会静默改写历史明细的翻译结果。
   */
  default List<ClaimItem> findPendingByFeeCode(Long tenantId, String feeCode) {
    return findByCriteria(
        Criteria.<ClaimItem>create()
            .eq(ClaimItem::getTenantId, tenantId)
            .eq(ClaimItem::getFeeCode, feeCode)
            .eq(ClaimItem::getStdFeeCode, ""));
  }
}
