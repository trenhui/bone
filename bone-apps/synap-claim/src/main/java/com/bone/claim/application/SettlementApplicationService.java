package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.settlement.AccountType;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.repository.SettlementRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A7 资金应用服务（结算编排）。
 *
 * <p>每个写方法只持久化 {@code SettlementOrder} 一个聚合（CORE-06）；额度占用/释放/确认/退款全部经 独立事务的 {@link
 * QuotaApplicationService}（{@code REQUIRES_NEW}），跨聚合改动不并入结算事务—— 资金状态机只由结算聚合推进，额度三值模型在自己事务内强一致。
 */
@Service
@RequiredArgsConstructor
public class SettlementApplicationService {

  private final SettlementRepository settlementRepository;
  private final QuotaApplicationService quotaApplicationService;
  private final IndemnityApplicationService indemnityApplicationService;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  public record SettlementResult(
      String settlementNo, Long claimId, BigDecimal amount, String fundingStage, Integer attempt) {}

  /**
   * 建结算单 + 软占额度（幂等：同 claim+attempt 已存在且未终态 → 返回首次结果）。
   *
   * @param attempt 占额周期序号（§7.5）：同一案件多轮退款/重占时递增
   */
  @Transactional
  public SettlementResult create(
      long claimId, int attempt, AccountType accountType, String operatorId) {
    long tenantId = tenantPort.currentTenantId();

    SettlementOrder latest = settlementRepository.findLatestByClaimId(claimId);
    if (latest != null
        && latest.getAttempt() == attempt
        && !latest.fundingStageEnum().isTerminal()) {
      return toResult(latest); // 幂等返回首次
    }

    BigDecimal amount = resolveAmount(claimId);
    ClaimWithItemsProjection ctx = claimCaseApplicationService.getClaimWithItems(claimId).get(0);
    String settlementNo = "SO" + claimId + "-" + attempt;

    SettlementOrder order =
        SettlementOrder.create(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            settlementNo,
            claimId,
            ctx.getClaimNo(),
            amount,
            accountType,
            attempt);
    settlementRepository.insert(order);

    // 额度软占（独立事务）
    quotaApplicationService.reserve(
        tenantId,
        ctx.getInsuredIdHash(),
        ctx.getPolicyNo(),
        ctx.getPlanId(),
        claimId,
        ctx.getClaimNo(),
        amount,
        settlementNo,
        attempt);

    domainEventPublisher.publishFrom(order);
    return toResult(order);
  }

  /**
   * 审核通过（第一道认证）：小额直接硬预占，≥1 万进入复核冻结。
   *
   * <p>职责分离的比较基准（第一审核人）取自赔案聚合的 {@code firstReviewerId}——那是经 token 认证、
   * 实际领取核赔任务的人。该值<b>不再接受调用方传入</b>：由客户端指定「谁被排除在复核之外」等于自我豁免。
   *
   * <p>复核人名单同样没有入参：大额的第二道认证由 {@link #confirmReview} 以另一个真实身份完成。
   */
  @Transactional
  public SettlementResult auditPass(long claimId, String operatorId) {
    SettlementOrder order = requireMutable(claimId);
    String firstReviewerId =
        claimCaseApplicationService.getClaimWithItems(claimId).get(0).getFirstReviewerId();
    order.auditPass(operatorId, firstReviewerId);
    settlementRepository.save(order);
    domainEventPublisher.publishFrom(order);
    return toResult(order);
  }

  /**
   * 大额复核放行（第二道认证）：{@code REVIEW_FREEZE → HARD_RESERVED}。
   *
   * <p>复核人与审核发起人的区分由聚合判定（同一个 userId 会被拒），本层不提供任何「指定复核人」参数。
   */
  @Transactional
  public SettlementResult confirmReview(long claimId, String operatorId) {
    SettlementOrder order = requireMutable(claimId);
    String firstReviewerId =
        claimCaseApplicationService.getClaimWithItems(claimId).get(0).getFirstReviewerId();
    order.confirmReview(operatorId, firstReviewerId);
    settlementRepository.save(order);
    domainEventPublisher.publishFrom(order);
    return toResult(order);
  }

  /** 支付成功（PAID）：额度 frozen→claim 确认扣减。 */
  @Transactional
  public SettlementResult pay(long claimId, String operatorId) {
    SettlementOrder order = requireMutable(claimId);
    order.pay(operatorId);
    settlementRepository.save(order);

    BigDecimal amount = order.getAmount();
    ClaimWithItemsProjection ctx = claimCaseApplicationService.getClaimWithItems(claimId).get(0);
    int attempt = order.getAttempt();
    quotaApplicationService.confirmPay(
        order.getTenantId(),
        ctx.getInsuredIdHash(),
        ctx.getPolicyNo(),
        ctx.getPlanId(),
        claimId,
        order.getClaimNo(),
        amount,
        order.getSettlementNo(),
        attempt);

    // 小额直付结案（42 → 50）：大额案在双人复核通过时已终态（44→50），此处按状态幂等跳过。
    // 没有这一步，<1 万案件支付完成后永远停在 ADJUSTED——不能结案、进不了后督抽检样本。
    com.bone.claim.domain.model.claim.ClaimCase claim =
        claimCaseApplicationService.getClaimAggregate(claimId);
    if (claim.status() == com.bone.claim.domain.model.claim.valueobject.ClaimStatus.ADJUSTED) {
      claim.finishUponPaid(operatorId);
      claimCaseApplicationService.saveClaim(claim);
    }

    domainEventPublisher.publishFrom(order);
    return toResult(order);
  }

  /** 撤销 / 拒赔 / 支付失败 → 释放预占。 */
  @Transactional
  public SettlementResult cancel(long claimId, String operatorId, boolean payFailed) {
    SettlementOrder order = requireMutable(claimId);
    order.cancel(operatorId, payFailed);
    settlementRepository.save(order);

    BigDecimal amount = order.getAmount();
    ClaimWithItemsProjection ctx = claimCaseApplicationService.getClaimWithItems(claimId).get(0);
    quotaApplicationService.release(
        order.getTenantId(),
        ctx.getInsuredIdHash(),
        ctx.getPolicyNo(),
        ctx.getPlanId(),
        claimId,
        order.getClaimNo(),
        amount,
        order.getSettlementNo(),
        order.getAttempt());

    domainEventPublisher.publishFrom(order);
    return toResult(order);
  }

  /** 已付退票退款（REFUNDED）：红冲并回冲 claimed。 */
  @Transactional
  public SettlementResult refund(long claimId, String operatorId) {
    SettlementOrder order = requireMutable(claimId);
    if (!order.isPaid()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_SETTLEMENT_CONFLICT, claimId);
    }
    order.refund(operatorId);
    settlementRepository.save(order);

    BigDecimal amount = order.getAmount();
    ClaimWithItemsProjection ctx = claimCaseApplicationService.getClaimWithItems(claimId).get(0);
    quotaApplicationService.refund(
        order.getTenantId(),
        ctx.getInsuredIdHash(),
        ctx.getPolicyNo(),
        ctx.getPlanId(),
        claimId,
        order.getClaimNo(),
        amount,
        order.getSettlementNo(),
        order.getAttempt());

    domainEventPublisher.publishFrom(order);
    return toResult(order);
  }

  @Transactional(readOnly = true)
  public SettlementResult latest(long claimId) {
    SettlementOrder order = settlementRepository.findLatestByClaimId(claimId);
    return order == null ? null : toResult(order);
  }

  // ===================== 内部 =====================

  private BigDecimal resolveAmount(long claimId) {
    IndemnityApplicationService.AdjustmentResult adj = indemnityApplicationService.latest(claimId);
    if (adj == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, claimId);
    }
    return adj.totalAmount();
  }

  private SettlementOrder requireMutable(long claimId) {
    SettlementOrder order = settlementRepository.findLatestByClaimId(claimId);
    if (order == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_SETTLEMENT_NOT_FOUND, claimId);
    }
    if (order.fundingStageEnum().isTerminal()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_SETTLEMENT_CONFLICT, claimId);
    }
    return order;
  }

  private SettlementResult toResult(SettlementOrder order) {
    return new SettlementResult(
        order.getSettlementNo(),
        order.getClaimId(),
        order.getAmount(),
        order.getFundingStage(),
        order.getAttempt());
  }
}
