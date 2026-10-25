package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.adjustment.AdjustmentLine;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A6 理算应用服务（金额计算的编排入口，AI 不参与）。
 *
 * <p>一事务一聚合（CORE-06）：本服务每个方法只写 {@code Adjustment} 一个聚合—— 额度占额按 §5.3「理算只算不占额」归 A7 资金阶段（{@code
 * SettlementOrder} 创建时 SOFT/HARD），不在此处调用，规避"一事务改两个聚合"。
 */
@Service
@RequiredArgsConstructor
public class IndemnityApplicationService {

  private final AdjustmentRepository adjustmentRepository;
  private final LiabilityRepository liabilityRepository;
  private final EvidenceRepository evidenceRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  /** 理算结果（含逐行归因，供拒赔五要素「事实认定」引用）。 */
  public record AdjustmentResult(
      Long calculationId,
      BigDecimal totalAmount,
      String formulaHash,
      String snapshot,
      List<AdjustmentLine> lines) {}

  /**
   * 执行理算（产出即 {@code FROZEN}）。
   *
   * <p>前置：① 证据集已锁定（§4.3，未锁定即 422——理算输入事后可变则"同输入同输出"无从谈起）； ② 计划下有生效责任配置（缺失即 422，绝不"默认 90%"隐式放行，§8.2
   * R-RAT）。
   */
  @Transactional
  public AdjustmentResult calculate(long claimId, String ruleVersion) {
    long tenantId = tenantPort.currentTenantId();

    List<Evidence> evidences = evidenceRepository.findByClaimId(claimId);
    if (evidences.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, claimId);
    }
    boolean allLocked = evidences.stream().allMatch(Evidence::isLocked);
    if (!allLocked) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_EVIDENCE_NOT_LOCKED, claimId);
    }

    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    AdjLiability liability = resolveLiability(rows);

    Adjustment adjustment;
    try {
      adjustment =
          Adjustment.calculate(
              DistributedIdGenerator.generateLongId(),
              tenantId,
              claimId,
              rows,
              liability,
              ruleVersion);
    } catch (DomainException ex) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, claimId, ex);
    }
    adjustmentRepository.insert(adjustment);
    domainEventPublisher.publishFrom(adjustment);
    return new AdjustmentResult(
        adjustment.getId(),
        adjustment.getTotalAmount(),
        adjustment.getFormulaHash(),
        adjustment.getFormulaSnapshot(),
        adjustmentRepository.findLinesByCalculationId(adjustment.getId()));
  }

  /** 重算（规则/责任版本变更后；已 CONFIRMED 拒绝，由聚合抛领域异常）。 */
  @Transactional
  public AdjustmentResult recalculate(long claimId, String ruleVersion) {
    Adjustment latest = adjustmentRepository.findLatestByClaimId(claimId);
    if (latest == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, claimId);
    }
    List<ClaimWithItemsProjection> rows = claimCaseApplicationService.getClaimWithItems(claimId);
    AdjLiability liability = resolveLiability(rows);
    latest.recalculate(rows, liability);
    adjustmentRepository.save(latest);
    domainEventPublisher.publishFrom(latest);
    return new AdjustmentResult(
        latest.getId(),
        latest.getTotalAmount(),
        latest.getFormulaHash(),
        latest.getFormulaSnapshot(),
        adjustmentRepository.findLinesByCalculationId(latest.getId()));
  }

  /** 核赔确认（frozen 语义：理算结果锁定，等 A7 资金阶段真正占额）。 */
  @Transactional
  public void confirm(long claimId) {
    Adjustment latest = adjustmentRepository.findLatestByClaimId(claimId);
    if (latest == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, claimId);
    }
    latest.confirm();
    adjustmentRepository.save(latest);
    domainEventPublisher.publishFrom(latest);
  }

  @Transactional(readOnly = true)
  public AdjustmentResult latest(long claimId) {
    Adjustment latest = adjustmentRepository.findLatestByClaimId(claimId);
    if (latest == null) {
      return null;
    }
    return new AdjustmentResult(
        latest.getId(),
        latest.getTotalAmount(),
        latest.getFormulaHash(),
        latest.getFormulaSnapshot(),
        adjustmentRepository.findLinesByCalculationId(latest.getId()));
  }

  /**
   * 取当期生效责任（多责任时取第一条；多责任链式赔付属二期）。
   *
   * <p>取不到即 422——责任配置是理算的唯一依据，没有"默认责任"。
   */
  private AdjLiability resolveLiability(List<ClaimWithItemsProjection> rows) {
    Long planId = rows.isEmpty() ? null : rows.get(0).getPlanId();
    if (planId == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, "planId 缺失");
    }
    List<AdjLiability> liabilities = liabilityRepository.findActiveByPlanId(planId);
    if (liabilities.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, planId);
    }
    return liabilities.get(0);
  }
}
