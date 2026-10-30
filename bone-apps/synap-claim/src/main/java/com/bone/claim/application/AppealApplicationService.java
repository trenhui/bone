package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.appeal.AppealRecord;
import com.bone.claim.domain.model.appeal.AppealReviewResult;
import com.bone.claim.domain.model.appeal.AppealStatus;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.repository.AppealRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A8 申诉应用服务（48h 复议编排）。
 *
 * <p>每个写方法只持久化 {@code AppealRecord} 一个聚合（CORE-06）；案件状态流转（45/50 → 60 → 41/45） 经 {@link
 * ClaimCaseApplicationService} 在同一事务内推进——该流转只改 {@code ClaimCase} 一个聚合， 不构成"一事务多聚合写"（CORE-06
 * 约束的是"多聚合各自动 persist"，此处为单一聚合的编排）。
 */
@Service
@RequiredArgsConstructor
public class AppealApplicationService {

  private final AppealRepository appealRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  public record AppealResult(
      String appealNo, Long claimId, String originalStatus, String status, String reviewResult) {}

  /** 发起申诉（受理）：案件须已终态且未超 48h；幂等——同一受理中申诉重复提交返回首次。 */
  @Transactional
  public AppealResult submit(long claimId, String submittedBy, String reason) {
    long tenantId = tenantPort.currentTenantId();

    AppealRecord existing = appealRepository.findLatestByClaimId(claimId);
    if (existing != null && existing.statusEnum() == AppealStatus.SUBMITTED) {
      return toResult(existing);
    }

    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    if (!claim.finished()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, claimId);
    }
    // 超 48h 窗口先在这里翻译成业务码：域层 AppealRecord#submit 仍会再判一次（不变量不外包），
    // 但域层抛的是 DomainException ⇒ 全局 handler 落成 400 + COMMON_VALIDATION_FAILED，
    // 前端只能显示「参数校验失败：申诉已超 48h 时限（§6.6），不可受理」——把内部分节号泄露给柜面，
    // 且与真正的参数错同码。closedAt 为 null 时不在本处报错：那是「数据缺失」，交给域层给出精确原因。
    if (claim.closedAt() != null && !AppealRecord.withinAppealWindow(claim.closedAt())) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_APPEAL_EXPIRED, claim.getClaimNo());
    }
    // 进入申诉前的终态，存枚举名（与 t_claim_case.status 同口径，见 ClaimStatus 类注释）
    String originalStatus = claim.status().name();
    claim.enterAppeal(submittedBy);
    claimCaseApplicationService.saveClaim(claim);

    AppealRecord appeal =
        AppealRecord.submit(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            "AP" + tenantId + claimId,
            claimId,
            claim.getClaimNo(),
            originalStatus,
            submittedBy,
            claim.closedAt());
    appealRepository.insert(appeal);
    domainEventPublisher.publishFrom(appeal);
    return toResult(appeal);
  }

  /** 复议结论：维持（→45） / 推翻重启审核（→41）。 */
  @Transactional
  public AppealResult review(
      long claimId, AppealReviewResult result, String reviewedBy, String reason) {
    AppealRecord appeal = appealRepository.findLatestByClaimId(claimId);
    if (appeal == null || appeal.statusEnum() != AppealStatus.SUBMITTED) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_APPEAL_NOT_FOUND, claimId);
    }
    appeal.review(result, reviewedBy, reason);
    appealRepository.save(appeal);

    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    if (result == AppealReviewResult.UPHELD) {
      claim.resolveAppealUpheld(reviewedBy);
    } else {
      claim.resolveAppealReopen(reviewedBy);
    }
    claimCaseApplicationService.saveClaim(claim);

    domainEventPublisher.publishFrom(appeal);
    return toResult(appeal);
  }

  @Transactional(readOnly = true)
  public AppealResult latest(long claimId) {
    AppealRecord appeal = appealRepository.findLatestByClaimId(claimId);
    return appeal == null ? null : toResult(appeal);
  }

  private AppealResult toResult(AppealRecord appeal) {
    return new AppealResult(
        appeal.getAppealNo(),
        appeal.getClaimId(),
        appeal.getOriginalStatus(),
        appeal.getStatus(),
        appeal.getReviewResult());
  }
}
