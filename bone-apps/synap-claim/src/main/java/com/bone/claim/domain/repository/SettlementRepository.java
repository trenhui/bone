package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.settlement.FundingEvent;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.LocalDate;
import java.util.List;

/** A7 结算单仓储（结算单 + 资金流水分开查询：与 blueprint 同款，集合不随聚合回填）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface SettlementRepository extends Repository<SettlementOrder, Long> {

  SettlementOrder findLatestByClaimId(@Param("claimId") long claimId);

  /** 本租户某业务日创建的结算单（对账 PAYMENT 通道输入）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<SettlementOrder> findByBizDate(@Param("bizDate") LocalDate bizDate);

  /** 结算单的资金流水（对账与审计事实源，append-only）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<FundingEvent> findFundingEventsBySettlementId(@Param("settlementId") long settlementId);
}
