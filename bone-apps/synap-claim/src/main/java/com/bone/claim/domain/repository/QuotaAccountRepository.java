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

  /** 本租户全部额度账户（对账守恒核对用，{@code ReconciliationApplicationService} 在租户上下文内调用）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<QuotaAccount> findAllAccounts();

  /**
   * 按保单号取额度账户（配置面：某保单下的个人额度列表）。
   *
   * <p>与 {@link #findAllAccounts} 的区别：后者是全租户扫描（仅对账用，且被 ArchUnit 收紧到 {@code adapter.schedule}
   * 可调），本方法带 {@code policyNo} 过滤，属正常业务查询。
   */
  @TenantScope(TenantScopeMode.AUTO)
  List<QuotaAccount> findAccountsByPolicyNo(@Param("policyNo") String policyNo);

  /** 按保单号 + 被保人取额度账户（配置面：某被保人的额度明细；一个被保人可有多个计划/年度的账户）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<QuotaAccount> findAccountsByPolicyAndInsured(
      @Param("policyNo") String policyNo, @Param("insuredIdHash") String insuredIdHash);
}
