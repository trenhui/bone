package com.bone.claim.application;

import com.bone.claim.application.port.out.ClaimIntakePort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.valueobject.SignCaseOutcome;
import com.bone.claim.domain.model.stakeholder.ClaimStakeholder;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimSignBatchRepository;
import com.bone.claim.domain.repository.ClaimStakeholderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 自动建案流水线：建案、受理、分配初审各走独立事务，失败停在合法人工态。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimSignCasePipeline {

  private final ClaimCaseRepository caseRepository;
  private final ClaimStakeholderRepository stakeholderRepository;
  private final ClaimIntakePort intakePort;
  private final ClaimSignBatchRepository batchRepository;
  private final DomainEventPublisher domainEventPublisher;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ClaimSignCaseCreation create(long tenantId, long batchId, SignBatchItem item) {
    if (item.getCreatedCaseId() != null || item.getClaimId() != null) {
      long linkedId = item.getCreatedCaseId() == null ? item.getClaimId() : item.getCreatedCaseId();
      batchRepository.markCaseForItem(itemId(item), tenantId, SignBatchItem.CASE_SKIPPED);
      item.markSkipped(SignBatchItem.CASE_SKIPPED);
      return new ClaimSignCaseCreation(
          SignCaseOutcome.SKIPPED, linkedId, item.getClaimNo(), "明细已关联案件，跳过建案");
    }
    if (!item.needsCaseCreation()) {
      batchRepository.markCaseForItem(itemId(item), tenantId, SignBatchItem.CASE_AWAIT_MANUAL);
      item.markSkipped(SignBatchItem.CASE_AWAIT_MANUAL);
      return new ClaimSignCaseCreation(
          SignCaseOutcome.AWAIT_MANUAL, null, item.getClaimNo(), "建案要素缺失，待人工补录");
    }
    ClaimCase existing = caseRepository.findByIdempotencyKey(tenantId, item.getBusinessKey());
    if (existing != null) {
      batchRepository.linkCaseForItem(
          itemId(item), tenantId, existing.getId(), SignBatchItem.CASE_SKIPPED);
      item.linkCase(existing.getId(), SignBatchItem.CASE_SKIPPED);
      return new ClaimSignCaseCreation(
          SignCaseOutcome.SKIPPED, existing.getId(), existing.getClaimNo(), "幂等命中，沿用首次建案结果");
    }

    long reportId = DistributedIdGenerator.generateLongId();
    long claimId = DistributedIdGenerator.generateLongId();
    ClaimReport report =
        ClaimReport.open(
            reportId,
            tenantId,
            item.getPolicyNo(),
            item.getPlanId(),
            item.getInsuredIdNo(),
            item.getClaimType(),
            item.getOutInsureTime(),
            "",
            item.getSourceChannel());
    BigDecimal amount =
        item.getClaimAmount() == null || item.getClaimAmount().signum() <= 0
            ? BigDecimal.ZERO
            : item.getClaimAmount();
    ClaimItem placeholder =
        ClaimItem.of(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            claimId,
            "签收导入占位费用",
            "SIGN-IMPORT",
            ItemCategory.SERVICE,
            BigDecimal.ONE,
            amount,
            amount);
    // 理算只累计明细级 valid_amount（案件级金额不参与计算）。回执金额是可信入参时，
    // 自动录入同时写入合理费用，否则即使案件金额已透传，A6 仍按 0 元算出零赔付。
    if (amount.signum() > 0) {
      placeholder.approveValidAmount(amount);
    }
    ClaimCase claim =
        ClaimCase.draft(
            claimId,
            tenantId,
            reportId,
            report.getPolicyNo(),
            report.getPlanId(),
            report.getInsuredIdHash(),
            report.getClaimType(),
            null,
            null,
            report.getOutInsureTime(),
            item.getClaimNo(),
            item.getBusinessKey(),
            "",
            List.of(placeholder));
    claim.accept();
    try {
      intakePort.persistIntake(report, claim);
    } catch (DataIntegrityViolationException duplicate) {
      // 抛出自定义异常让 REQUIRES_NEW 事务整体回滚，避免先插报案、后插赔案失败留下孤儿报案。
      ClaimCase winner = caseRepository.findByIdempotencyKey(tenantId, item.getBusinessKey());
      if (winner == null) {
        throw duplicate;
      }
      throw new SignCaseDuplicateException(winner.getId(), winner.getClaimNo());
    }
    stakeholderRepository.save(
        ClaimStakeholder.create(
            tenantId,
            claimId,
            ClaimStakeholder.ROLE_INSURED,
            item.getInsuredIdNo(),
            item.getInsuredName(),
            "",
            "",
            "",
            "",
            Instant.now()));
    domainEventPublisher.publishFrom(claim);
    batchRepository.linkCaseForItem(itemId(item), tenantId, claimId, SignBatchItem.CASE_NEW);
    item.linkCase(claimId, SignBatchItem.CASE_NEW);
    return new ClaimSignCaseCreation(
        SignCaseOutcome.NEW, claimId, claim.getClaimNo(), "自动建案成功并已受理");
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void allocate(long tenantId, long claimId) {
    ClaimCase claim = requireClaim(tenantId, claimId);
    claim.allocateToPreExam("SYSTEM");
    caseRepository.save(claim);
    domainEventPublisher.publishFrom(claim);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void startPreExam(long tenantId, long claimId) {
    ClaimCase claim = requireClaim(tenantId, claimId);
    claim.startPreExam(true, "SYSTEM");
    caseRepository.save(claim);
    domainEventPublisher.publishFrom(claim);
  }

  private ClaimCase requireClaim(long tenantId, long claimId) {
    ClaimCase claim = caseRepository.findById(claimId);
    if (claim == null || !Long.valueOf(tenantId).equals(claim.getTenantId())) {
      throw new IllegalStateException("自动建案生成的案件不存在或租户不匹配: claimId=" + claimId);
    }
    return claim;
  }

  private static long itemId(SignBatchItem item) {
    return item.getId() == null ? 0L : item.getId();
  }
}
