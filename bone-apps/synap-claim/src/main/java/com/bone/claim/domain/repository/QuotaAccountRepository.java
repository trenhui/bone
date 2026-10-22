package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * A5 额度账户仓储。
 *
 * <p>账户与流水分开查询：流水 append-only 且量大，随聚合回填会拖垮读取（同 blueprint 明细纪律）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface QuotaAccountRepository extends Repository<QuotaAccount, Long> {

  QuotaAccount findById(@Param("quotaAccountId") long quotaAccountId);

  /**
   * 按被保人 + 计划 + 年度 + 维度定位账户（UK 同构：{@code (tenant_id, insured_id_hash, policy_no, plan_id,
   * quota_year, quota_type, liability_uuid)}）。
   */
  @TenantScope(TenantScopeMode.AUTO)
  QuotaAccount findAccount(
      @Param("insuredIdHash") String insuredIdHash,
      @Param("policyNo") String policyNo,
      @Param("planId") long planId,
      @Param("quotaYear") int quotaYear,
      @Param("quotaType") String quotaType,
      @Param("liabilityUuid") String liabilityUuid);

  /** 账户流水（守恒校验与审计的数据源，§11.7 不变量 1）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<QuotaTransaction> findTransactions(@Param("quotaAccountId") long quotaAccountId);
}
