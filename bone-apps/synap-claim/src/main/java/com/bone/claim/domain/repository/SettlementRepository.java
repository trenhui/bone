package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.settlement.FundingEvent;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/** A7 结算单仓储（结算单 + 资金流水分开查询：与 blueprint 同款，集合不随聚合回填）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface SettlementRepository extends Repository<SettlementOrder, Long> {

  SettlementOrder findBySettlementNo(@Param("settlementNo") String settlementNo);

  SettlementOrder findLatestByClaimId(@Param("claimId") long claimId);

  List<FundingEvent> findFundingEventsBySettlementId(@Param("settlementId") long settlementId);
}
