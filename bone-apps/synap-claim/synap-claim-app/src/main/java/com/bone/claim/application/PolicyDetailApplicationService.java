package com.bone.claim.application;

import com.bone.claim.application.CodeMappingApplicationService.MappingHealthView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.ClaimCoverageOverride;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.CoverageOverrideRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保单详情聚合视图（task 4）：一张详情页回答「这个保单到底按什么规则赔、各主体的差异化在哪」。
 *
 * <p>聚合五块：
 *
 * <ul>
 *   <li>保单概要（主体/保司/状态/版本）；
 *   <li>主体映射健康度（四域覆盖率与未命中，{@code bizIdentityCode} 为空时缺省——存量保单尚未对齐主体）；
 *   <li>计划→责任树：ACTIVE 责任版本 + 费用规则 + 覆盖差异（本保单主体的覆盖行，含未发布）；
 *   <li>适用被保人名单（{@code insured_snapshot} 的假名化 ID，绝不出现明文证件）；
 *   <li>额度池账户状态（复用 {@code QuotaAccountRepository#findAccountsByPolicyNo}，三值口径）。
 * </ul>
 *
 * <p><b>被保人名单以保单快照为唯一真源</b>：额度账户只覆盖「已开账户」的人，保单可以先建后录被保人； 若用额度账户反推名单，会把历史账户里的孤儿 hash
 * 混进承保名单，或漏掉尚未开户的被保人。额度账户另以明细全量返回，供「额度池」Tab 核对。
 *
 * <p><b>为什么聚合而不是前端拼</b>：详情页原来要打 5 个接口自行拼树，责任/费用/覆盖三层的 组合语义（覆盖差异挂在哪个 uuid 上）散在调用方——契约收口到一处，语义漂移没了。
 */
@Service
public class PolicyDetailApplicationService {

  private final ClaimPolicyRepository claimPolicyRepository;
  private final ClaimPlanRepository claimPlanRepository;
  private final ClaimCoverageRepository claimCoverageRepository;
  private final LiabilityRepository liabilityRepository;
  private final LiabilityFeeRuleRepository feeRuleRepository;
  private final CoverageOverrideRepository overrideRepository;
  private final QuotaAccountRepository quotaAccountRepository;
  private final CodeMappingApplicationService codeMappingApplicationService;
  private final TenantPort tenantPort;

  public PolicyDetailApplicationService(
      ClaimPolicyRepository claimPolicyRepository,
      ClaimPlanRepository claimPlanRepository,
      ClaimCoverageRepository claimCoverageRepository,
      LiabilityRepository liabilityRepository,
      LiabilityFeeRuleRepository feeRuleRepository,
      CoverageOverrideRepository overrideRepository,
      QuotaAccountRepository quotaAccountRepository,
      CodeMappingApplicationService codeMappingApplicationService,
      TenantPort tenantPort) {
    this.claimPolicyRepository = claimPolicyRepository;
    this.claimPlanRepository = claimPlanRepository;
    this.claimCoverageRepository = claimCoverageRepository;
    this.liabilityRepository = liabilityRepository;
    this.feeRuleRepository = feeRuleRepository;
    this.overrideRepository = overrideRepository;
    this.quotaAccountRepository = quotaAccountRepository;
    this.codeMappingApplicationService = codeMappingApplicationService;
    this.tenantPort = tenantPort;
  }

  public record FeeRuleRow(
      String feeCode,
      String feeName,
      BigDecimal payPercent,
      BigDecimal limitAmount,
      Integer socialOnly) {}

  /** 覆盖差异行：本保单主体在该责任上的覆盖（含未发布，详情页可见但不标"生效"）。 */
  public record OverrideRow(String overrideField, String overrideValue, String status) {}

  public record LiabilityNode(
      String liabilityUuid,
      String liabilityCode,
      String liabilityType,
      BigDecimal payPercent,
      BigDecimal liabilityLimit,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String status,
      List<FeeRuleRow> feeRules,
      List<OverrideRow> overrides) {}

  public record PlanDetailView(
      Long planId, String planCode, String planName, List<LiabilityNode> liabilities) {}

  /** 适用被保人名单行：假名化 ID + 该人名下额度账户的汇总（三值口径，不出现明文证件）。 */
  public record InsuredRosterRow(
      String insuredIdHash,
      int accountCount,
      BigDecimal quotaInitial,
      BigDecimal quotaClaimed,
      BigDecimal quotaFrozen,
      BigDecimal available) {}

  /** 额度池账户明细：available = initial − claimed − frozen（{@link QuotaAccount#available()}）。 */
  public record QuotaAccountRow(
      String insuredIdHash,
      Long planId,
      Integer quotaYear,
      String quotaType,
      String liabilityUuid,
      BigDecimal quotaInitial,
      BigDecimal quotaClaimed,
      BigDecimal quotaFrozen,
      BigDecimal available,
      Integer insuredState) {}

  public record PolicySummary(
      String policyNo,
      String policyName,
      String insurerName,
      String branchName,
      String insureName,
      String bizIdentityCode,
      String productType,
      String policyType,
      Instant effectiveStart,
      Instant effectiveEnd,
      String policyVersion,
      String configStatus,
      Long version) {

    static PolicySummary from(ClaimPolicy p) {
      return new PolicySummary(
          p.getPolicyNo(),
          p.getPolicyName(),
          p.getInsurerName(),
          p.getBranchName(),
          p.getInsureName(),
          p.getBizIdentityCode(),
          p.getProductType(),
          p.getPolicyType(),
          p.getEffectiveStart(),
          p.getEffectiveEnd(),
          p.getPolicyVersion(),
          p.configStatus(),
          p.getVersion());
    }
  }

  public record PolicyDetail(
      PolicySummary summary,
      List<MappingHealthView> insurerHealth,
      List<PlanDetailView> plans,
      List<InsuredRosterRow> insuredRoster,
      List<QuotaAccountRow> quotaAccounts) {}

  @Transactional(readOnly = true)
  public PolicyDetail detail(String policyNo) {
    long tenantId = tenantPort.currentTenantId();
    ClaimPolicy policy = claimPolicyRepository.findByPolicyNo(policyNo);
    if (policy == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_NOT_FOUND, policyNo);
    }

    List<MappingHealthView> health = new ArrayList<>();
    String biz = policy.getBizIdentityCode();
    if (biz != null && !biz.isBlank()) {
      for (MasterDataDomain domain : MasterDataDomain.values()) {
        try {
          health.add(codeMappingApplicationService.mappingHealth(biz, domain.code()));
        } catch (IllegalArgumentException ignored) {
          // 未知域码防御，跳过
        }
      }
    }

    List<PlanDetailView> plans = new ArrayList<>();
    for (ClaimPlan plan : claimPlanRepository.findByPolicyNo(policyNo)) {
      List<LiabilityNode> liabilities = new ArrayList<>();
      for (AdjLiability liability : liabilityRepository.findActiveByPlanId(plan.getId())) {
        liabilities.add(toNode(tenantId, policy, liability));
      }
      plans.add(
          new PlanDetailView(plan.getId(), plan.getPlanCode(), plan.getPlanName(), liabilities));
    }

    // ---- 额度池：一次按保单号取全部账户，避免逐被保人 N+1 ----
    List<QuotaAccount> accounts = quotaAccountRepository.findAccountsByPolicyNo(policyNo);
    List<QuotaAccountRow> quotaRows =
        accounts.stream()
            .map(
                a ->
                    new QuotaAccountRow(
                        a.getInsuredIdHash(),
                        a.getPlanId(),
                        a.getQuotaYear(),
                        a.getQuotaType(),
                        a.getLiabilityUuid(),
                        a.getQuotaInitial(),
                        a.getQuotaClaimed(),
                        a.getQuotaFrozen(),
                        a.available(),
                        a.getInsuredState()))
            .toList();

    // ---- 名单：以 insured_snapshot 为准，额度账户只做汇总补充 ----
    Map<String, List<QuotaAccount>> byInsured =
        accounts.stream().collect(Collectors.groupingBy(QuotaAccount::getInsuredIdHash));
    List<InsuredRosterRow> roster = new ArrayList<>();
    for (String hash : ClaimPolicy.parseInsuredHashes(policy.getInsuredSnapshot())) {
      List<QuotaAccount> own = byInsured.getOrDefault(hash, List.of());
      roster.add(
          new InsuredRosterRow(
              hash,
              own.size(),
              sum(own, QuotaAccount::getQuotaInitial),
              sum(own, QuotaAccount::getQuotaClaimed),
              sum(own, QuotaAccount::getQuotaFrozen),
              sum(own, QuotaAccount::available)));
    }

    return new PolicyDetail(PolicySummary.from(policy), health, plans, roster, quotaRows);
  }

  /** 空值按 0 计；null 字段不参与累加，避免脏数据把整张详情页打挂。 */
  private static BigDecimal sum(
      List<QuotaAccount> accounts, Function<QuotaAccount, BigDecimal> extractor) {
    return accounts.stream()
        .map(extractor)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private LiabilityNode toNode(long tenantId, ClaimPolicy policy, AdjLiability liability) {
    List<FeeRuleRow> feeRules =
        feeRuleRepository.findByLiabilityUuid(tenantId, liability.getLiabilityUuid()).stream()
            .map(
                r ->
                    new FeeRuleRow(
                        r.getFeeCode(),
                        r.getFeeName(),
                        r.getPayPercent(),
                        r.getLimitAmount(),
                        r.getSocialOnly()))
            .toList();
    List<OverrideRow> overrides = new ArrayList<>();
    String biz = policy.getBizIdentityCode();
    if (biz != null && !biz.isBlank()) {
      for (ClaimCoverageOverride o :
          overrideRepository.findActiveByBizAndCoverage(
              tenantId, biz, liability.getLiabilityUuid())) {
        overrides.add(new OverrideRow(o.getOverrideField(), o.getOverrideValue(), o.getStatus()));
      }
    }
    return new LiabilityNode(
        liability.getLiabilityUuid(),
        liability.getLiabilityCode(),
        liability.getLiabilityType(),
        liability.getPayPercent(),
        liability.getLiabilityLimit(),
        liability.limitHospitalCodes(),
        liability.limitDiagnosisCodes(),
        liability.getStatus(),
        feeRules,
        overrides);
  }
}
