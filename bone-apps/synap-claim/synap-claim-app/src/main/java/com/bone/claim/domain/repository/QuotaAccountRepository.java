package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
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

  /**
   * 全租户额度流水（对账批量取数用，避免逐账户 {@code findTransactions} 的 N+1）。
   *
   * <p>流水 append-only，逐账户查的总行数与一次取回相同；对账本就是全租户扫描，故一次取回不额外放大数据量， 只把查询次数从 N 降为 1。调用方按 {@code
   * quotaAccountId} 分组还原每账户流水。
   */
  @TenantScope(TenantScopeMode.AUTO)
  List<QuotaTransaction> findAllTransactions();

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

  /**
   * 分页查额度账户（配置面列表：保单 + 被保人关键字 + 维度 + 减人状态筛选）。
   *
   * <p>list 与 count 共用 {@link #accountListCriteria}，避免两端条件口径漂移（与 {@code
   * ClaimPolicyRepository#findPolicyPage/countPolicyPage} 同款约定）。
   *
   * @param tenantId 租户
   * @param policyNo 保单号（必填：配置面按保单维度查看）
   * @param keyword 被保人假名化 ID 模糊匹配（可空）
   * @param quotaType 额度维度过滤（可空）
   * @param removed 减人状态过滤（null 不过滤）
   * @param page 页码（从 1 起）
   * @param size 每页条数
   */
  default List<QuotaAccount> findAccountsPage(
      Long tenantId,
      String policyNo,
      String keyword,
      String quotaType,
      Boolean removed,
      int page,
      int size) {
    Criteria<QuotaAccount> criteria =
        accountListCriteria(tenantId, policyNo, keyword, quotaType, removed);
    criteria.orderByDesc(QuotaAccount::getCreatedAt).page(page, size);
    return findByCriteria(criteria);
  }

  /** 分页总数（与 {@link #findAccountsPage} 同口径）。 */
  default long countAccountsPage(
      Long tenantId, String policyNo, String keyword, String quotaType, Boolean removed) {
    return countByCriteria(accountListCriteria(tenantId, policyNo, keyword, quotaType, removed));
  }

  /**
   * 同筛选条件的全量列表（导出用，不翻页）。
   *
   * <p>与 {@link #findAccountsByPolicyNo} 的区别：本方法带被保人关键字 / 维度 / 减人状态筛选， 导出的 CSV
   * 必须与列表页所见一致（同一套条件），否则"页面看着 3 条、导出 300 条"。
   */
  default List<QuotaAccount> findAccountsFiltered(
      Long tenantId, String policyNo, String keyword, String quotaType, Boolean removed) {
    Criteria<QuotaAccount> criteria =
        accountListCriteria(tenantId, policyNo, keyword, quotaType, removed);
    criteria.orderByDesc(QuotaAccount::getCreatedAt);
    return findByCriteria(criteria);
  }

  /**
   * 列表查询条件（list / count / 导出三方共用，避免口径漂移）。
   *
   * <p><b>关键字对被保人假名化 ID 做 LIKE</b>：配置面按人查额度是主场景（"这个人的账户还剩多少"）， 精确匹配要求运营先复制完整 HMAC 前缀，不可用。
   *
   * <p><b>排序固定 {@code createdAt} 倒序</b>：自然序在分页下不保证跨页稳定，可能重复/丢行。
   *
   * @param tenantId 租户
   * @param policyNo 保单号（必填）
   * @param keyword 被保人关键字（空/空白视为不过滤）
   * @param quotaType 额度维度（空/空白视为不过滤）
   * @param removed 减人状态（null 视为不过滤）
   */
  static Criteria<QuotaAccount> accountListCriteria(
      Long tenantId, String policyNo, String keyword, String quotaType, Boolean removed) {
    Criteria<QuotaAccount> criteria =
        Criteria.<QuotaAccount>create()
            .eq(QuotaAccount::getTenantId, tenantId)
            .eq(QuotaAccount::getPolicyNo, policyNo);
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    if (hasKeyword) {
      criteria.like(QuotaAccount::getInsuredIdHash, "%" + keyword.trim() + "%");
    }
    criteria.eq(quotaType != null && !quotaType.isBlank(), QuotaAccount::getQuotaType, quotaType);
    if (removed != null) {
      // 减人状态落在 insured_state（0 正常 / 1 减人），按整型等值过滤而非布尔列
      criteria.eq(QuotaAccount::getInsuredState, removed ? 1 : 0);
    }
    return criteria;
  }
}
