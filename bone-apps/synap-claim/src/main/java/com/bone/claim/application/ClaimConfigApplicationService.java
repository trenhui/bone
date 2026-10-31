package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.policy.ClaimCoverage;
import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimCoverageRepository;
import com.bone.claim.domain.repository.ClaimPlanRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.security.auth.CurrentOperator;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保单 / 计划 / 险种配置应用服务（配置面）。
 *
 * <p><b>与理赔作业面（{@code ClaimCaseApplicationService}）的分工</b>：作业面处理"一件赔案从报案到结案"，
 * 本类处理"一张保单配了什么计划、计划下配了什么险种与责任"。两者共享同一套仓储，但事务边界完全独立 （配置面一次事务只碰一张聚合，符合 CORE-06 一事务一聚合）。
 *
 * <p><b>为什么配置面要独立成服务而不是塞进作业面</b>：作业面的每个方法都带"案件状态机 + 幂等 + 复核"， 把配置 CRUD
 * 混进去会让那个类同时承担两种节奏完全不同的业务（一个是逐单流转，一个是低频批量编辑）。 分开后各自的测试面也小得多。
 *
 * <p><b>租户口径</b>：一律经 {@link TenantPort} 取当前租户（ArchUnit 禁业务层直读 {@code TenantContext}）。
 */
@Service
@RequiredArgsConstructor
public class ClaimConfigApplicationService {

  private final ClaimPolicyRepository claimPolicyRepository;
  private final ClaimPlanRepository claimPlanRepository;
  private final ClaimCoverageRepository claimCoverageRepository;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  // ===================== 保单 =====================

  /** 保单列表（分页；支持保单号模糊查 + 规则开关过滤）。 */
  @Transactional(readOnly = true)
  public PageResult<PolicyView> listPolicies(
      String keyword, String configStatus, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    List<ClaimPolicy> records =
        claimPolicyRepository.findPolicyPage(tenantId, keyword, configStatus, page, size);
    long total = claimPolicyRepository.countPolicyPage(tenantId, keyword, configStatus);
    return PageResult.of(records.stream().map(PolicyView::from).toList(), total, page, size);
  }

  /** 保单详情。 */
  @Transactional(readOnly = true)
  public PolicyView getPolicy(String policyNo) {
    return PolicyView.from(requirePolicy(policyNo));
  }

  /** 新建保单（草稿态：规则未启用，需另行启用）。 */
  @Transactional
  public PolicyView createPolicy(CreatePolicy command) {
    long tenantId = tenantPort.currentTenantId();
    if (claimPolicyRepository.findByPolicyNo(command.policyNo()) != null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_DUPLICATED, command.policyNo());
    }
    ClaimPolicy policy =
        ClaimPolicy.open(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            command.policyNo(),
            command.policyName(),
            command.insurerName(),
            command.branchName(),
            command.insureName(),
            command.bizIdentityCode(),
            command.productType(),
            command.effectiveStart(),
            command.effectiveEnd(),
            command.waitingPeriod(),
            command.policyVersion(),
            command.insuredSnapshot(),
            Instant.now());
    claimPolicyRepository.save(policy);
    domainEventPublisher.publishFrom(policy);
    return PolicyView.from(policy);
  }

  /** 启用保单责任（INACTIVE/PAUSE → ACTIVE）。 */
  @Transactional
  public PolicyView activatePolicy(String policyNo) {
    ClaimPolicy policy = requirePolicy(policyNo);
    policy.activate(CurrentOperator.requireId());
    claimPolicyRepository.save(policy);
    domainEventPublisher.publishFrom(policy);
    return PolicyView.from(policy);
  }

  /** 停用保单责任（ACTIVE → PAUSE）。 */
  @Transactional
  public PolicyView pausePolicy(String policyNo) {
    ClaimPolicy policy = requirePolicy(policyNo);
    policy.pause(CurrentOperator.requireId());
    claimPolicyRepository.save(policy);
    domainEventPublisher.publishFrom(policy);
    return PolicyView.from(policy);
  }

  /** 编辑保单可修正字段（不改动保单号与规则开关）。启用中的保单锁定承保期间。 */
  @Transactional
  public PolicyView updatePolicy(String policyNo, UpdatePolicy command) {
    ClaimPolicy policy = requirePolicy(policyNo);
    policy.edit(
        command.policyName(),
        command.insurerName(),
        command.branchName(),
        command.insureName(),
        command.bizIdentityCode(),
        command.productType(),
        command.effectiveStart(),
        command.effectiveEnd(),
        command.waitingPeriod(),
        command.policyVersion(),
        command.insuredSnapshot());
    claimPolicyRepository.save(policy);
    domainEventPublisher.publishFrom(policy);
    return PolicyView.from(policy);
  }

  // ===================== 计划 =====================

  /** 某保单下的计划列表。 */
  @Transactional(readOnly = true)
  public List<PlanView> listPlans(String policyNo) {
    requirePolicy(policyNo);
    return claimPlanRepository.findByPolicyNo(policyNo).stream().map(PlanView::from).toList();
  }

  /** 新建计划（草稿态）。 */
  @Transactional
  public PlanView createPlan(CreatePlan command) {
    long tenantId = tenantPort.currentTenantId();
    requirePolicy(command.policyNo());
    if (claimPlanRepository.findByPolicyNoAndCode(command.policyNo(), command.planCode()) != null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_PLAN_DUPLICATED, command.planCode());
    }
    ClaimPlan plan =
        ClaimPlan.draft(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            command.policyNo(),
            command.planCode(),
            command.planName(),
            command.planLimit(),
            command.waitingPeriod(),
            command.versionNo());
    claimPlanRepository.save(plan);
    domainEventPublisher.publishFrom(plan);
    return PlanView.from(plan);
  }

  /** 发布计划（DRAFT → ACTIVE）：激活后其下生效责任才参与理算。 */
  @Transactional
  public PlanView publishPlan(long planId) {
    ClaimPlan plan = requirePlan(planId);
    plan.publish(CurrentOperator.requireId());
    claimPlanRepository.save(plan);
    domainEventPublisher.publishFrom(plan);
    return PlanView.from(plan);
  }

  /** 下线计划。 */
  @Transactional
  public PlanView retirePlan(long planId) {
    ClaimPlan plan = requirePlan(planId);
    plan.retire(CurrentOperator.requireId());
    claimPlanRepository.save(plan);
    domainEventPublisher.publishFrom(plan);
    return PlanView.from(plan);
  }

  /** 编辑计划可修正字段（不改动计划编码与状态）。 */
  @Transactional
  public PlanView updatePlan(long planId, UpdatePlan command) {
    ClaimPlan plan = requirePlan(planId);
    plan.edit(
        command.planName(), command.planLimit(), command.waitingPeriod(), command.versionNo());
    claimPlanRepository.save(plan);
    domainEventPublisher.publishFrom(plan);
    return PlanView.from(plan);
  }

  // ===================== 险种 =====================

  /** 某计划下的险种列表。 */
  @Transactional(readOnly = true)
  public List<CoverageView> listCoverages(long planId) {
    requirePlan(planId);
    return claimCoverageRepository.findByPlanId(planId).stream().map(CoverageView::from).toList();
  }

  /** 新建险种（草稿态）。 */
  @Transactional
  public CoverageView createCoverage(CreateCoverage command) {
    long tenantId = tenantPort.currentTenantId();
    requirePlan(command.planId());
    if (claimCoverageRepository.findByPlanIdAndCode(command.planId(), command.coverageCode())
        != null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_COVERAGE_DUPLICATED, command.coverageCode());
    }
    ClaimCoverage coverage =
        ClaimCoverage.draft(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            command.planId(),
            command.coverageCode(),
            command.coverageName(),
            command.coverageLimit(),
            command.liabilityType());
    claimCoverageRepository.save(coverage);
    domainEventPublisher.publishFrom(coverage);
    return CoverageView.from(coverage);
  }

  /** 编辑险种可修正字段（不改动险种编码与状态）。 */
  @Transactional
  public CoverageView updateCoverage(long coverageId, UpdateCoverage command) {
    ClaimCoverage coverage = requireCoverage(coverageId);
    coverage.edit(command.coverageName(), command.coverageLimit(), command.liabilityType());
    claimCoverageRepository.save(coverage);
    domainEventPublisher.publishFrom(coverage);
    return CoverageView.from(coverage);
  }

  // ===================== 内部 =====================

  private ClaimPolicy requirePolicy(String policyNo) {
    ClaimPolicy policy = claimPolicyRepository.findByPolicyNo(policyNo);
    if (policy == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_NOT_FOUND, policyNo);
    }
    return policy;
  }

  private ClaimPlan requirePlan(long planId) {
    ClaimPlan plan = claimPlanRepository.findPlanById(planId);
    if (plan == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_PLAN_NOT_FOUND, planId);
    }
    return plan;
  }

  private ClaimCoverage requireCoverage(long coverageId) {
    ClaimCoverage coverage = claimCoverageRepository.findCoverageById(coverageId);
    if (coverage == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_COVERAGE_NOT_FOUND, coverageId);
    }
    return coverage;
  }

  // ===================== 命令 =====================

  /** 新建保单命令。 */
  public record CreatePolicy(
      String policyNo,
      String policyName,
      String insurerName,
      String branchName,
      String insureName,
      String bizIdentityCode,
      String productType,
      Instant effectiveStart,
      Instant effectiveEnd,
      int waitingPeriod,
      String policyVersion,
      String insuredSnapshot) {}

  /** 新建计划命令。 */
  public record CreatePlan(
      String policyNo,
      String planCode,
      String planName,
      BigDecimal planLimit,
      int waitingPeriod,
      String versionNo) {}

  /** 新建险种命令。 */
  public record CreateCoverage(
      long planId,
      String coverageCode,
      String coverageName,
      BigDecimal coverageLimit,
      String liabilityType) {}

  /** 编辑保单命令（不改动保单号与规则开关）。 */
  public record UpdatePolicy(
      String policyName,
      String insurerName,
      String branchName,
      String insureName,
      String bizIdentityCode,
      String productType,
      Instant effectiveStart,
      Instant effectiveEnd,
      int waitingPeriod,
      String policyVersion,
      String insuredSnapshot) {}

  /** 编辑计划命令（不改动计划编码与状态）。 */
  public record UpdatePlan(
      String planName, BigDecimal planLimit, int waitingPeriod, String versionNo) {}

  /** 编辑险种命令（不改动险种编码与状态）。 */
  public record UpdateCoverage(
      String coverageName, BigDecimal coverageLimit, String liabilityType) {}

  // ===================== 视图 =====================

  /** 保单视图（配置面读模型；放在 application 层是因为它是给 adapter 消费的投影）。 */
  public record PolicyView(
      String policyNo,
      String policyName,
      String insurerName,
      String branchName,
      String insureName,
      String bizIdentityCode,
      String productType,
      Instant effectiveStart,
      Instant effectiveEnd,
      int waitingPeriod,
      String policyVersion,
      String insuredSnapshot,
      String configStatus,
      boolean stale,
      Instant createdAt,
      Instant updatedAt) {

    static PolicyView from(ClaimPolicy p) {
      return new PolicyView(
          p.getPolicyNo(),
          p.getPolicyName(),
          p.getInsurerName(),
          p.getBranchName(),
          p.getInsureName(),
          p.getBizIdentityCode(),
          p.getProductType(),
          p.getEffectiveStart(),
          p.getEffectiveEnd(),
          p.waitingPeriodDays(),
          p.getPolicyVersion(),
          p.getInsuredSnapshot(),
          p.configStatus(),
          p.isStale(),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }

  /** 计划视图。 */
  public record PlanView(
      String planId,
      String policyNo,
      String planCode,
      String planName,
      BigDecimal planLimit,
      int waitingPeriod,
      String versionNo,
      String status,
      Instant createdAt,
      Instant updatedAt) {

    static PlanView from(ClaimPlan p) {
      return new PlanView(
          String.valueOf(p.getId()),
          p.policyNo(),
          p.getPlanCode(),
          p.getPlanName(),
          p.getPlanLimit(),
          p.waitingPeriodDays(),
          p.getVersionNo(),
          p.status(),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }

  /** 险种视图。 */
  public record CoverageView(
      String coverageId,
      String planId,
      String coverageCode,
      String coverageName,
      BigDecimal coverageLimit,
      String liabilityType,
      String status,
      Instant createdAt,
      Instant updatedAt) {

    static CoverageView from(ClaimCoverage c) {
      return new CoverageView(
          String.valueOf(c.getId()),
          String.valueOf(c.planId()),
          c.getCoverageCode(),
          c.getCoverageName(),
          c.getCoverageLimit(),
          c.getLiabilityType(),
          c.status(),
          c.getCreatedAt(),
          c.getUpdatedAt());
    }
  }
}
