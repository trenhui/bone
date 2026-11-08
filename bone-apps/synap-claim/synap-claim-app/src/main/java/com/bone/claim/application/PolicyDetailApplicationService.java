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
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.CoverageOverrideRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保单详情聚合视图（task 4）：一张详情页回答「这个保单到底按什么规则赔、各主体的差异化在哪」。
 *
 * <p>聚合三块：
 *
 * <ul>
 *   <li>保单概要（主体/保司/状态/版本）；
 *   <li>主体映射健康度（四域覆盖率与未命中，{@code bizIdentityCode} 为空时缺省——存量保单尚未对齐主体）；
 *   <li>计划→责任树：ACTIVE 责任版本 + 费用规则 + 覆盖差异（本保单主体的覆盖行，含未发布）。
 * </ul>
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
  private final CodeMappingApplicationService codeMappingApplicationService;
  private final TenantPort tenantPort;

  public PolicyDetailApplicationService(
      ClaimPolicyRepository claimPolicyRepository,
      ClaimPlanRepository claimPlanRepository,
      ClaimCoverageRepository claimCoverageRepository,
      LiabilityRepository liabilityRepository,
      LiabilityFeeRuleRepository feeRuleRepository,
      CoverageOverrideRepository overrideRepository,
      CodeMappingApplicationService codeMappingApplicationService,
      TenantPort tenantPort) {
    this.claimPolicyRepository = claimPolicyRepository;
    this.claimPlanRepository = claimPlanRepository;
    this.claimCoverageRepository = claimCoverageRepository;
    this.liabilityRepository = liabilityRepository;
    this.feeRuleRepository = feeRuleRepository;
    this.overrideRepository = overrideRepository;
    this.codeMappingApplicationService = codeMappingApplicationService;
    this.tenantPort = tenantPort;
  }

  public record FeeRuleRow(
      String feeCode,
      String feeName,
      java.math.BigDecimal payPercent,
      java.math.BigDecimal limitAmount,
      Integer socialOnly) {}

  /** 覆盖差异行：本保单主体在该责任上的覆盖（含未发布，详情页可见但不标"生效"）。 */
  public record OverrideRow(String overrideField, String overrideValue, String status) {}

  public record LiabilityNode(
      String liabilityUuid,
      String liabilityCode,
      String liabilityType,
      java.math.BigDecimal payPercent,
      java.math.BigDecimal liabilityLimit,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String status,
      List<FeeRuleRow> feeRules,
      List<OverrideRow> overrides) {}

  public record PlanDetailView(
      Long planId, String planCode, String planName, List<LiabilityNode> liabilities) {}

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
      PolicySummary summary, List<MappingHealthView> insurerHealth, List<PlanDetailView> plans) {}

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

    return new PolicyDetail(PolicySummary.from(policy), health, plans);
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
