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
import java.util.List;
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
            ctx.claimNo(),
            amount,
            accountType,
            attempt);
    settlementRepository.insert(order);

    // 额度软占（独立事务）
    quotaApplicationService.reserve(
        tenantId,
        ctx.insuredIdHash(),
        ctx.policyNo(),
        ctx.planId(),
        claimId,
        ctx.claimNo(),
        amount,
        settlementNo,
        attempt);

    domainEventPublisher.publishFrom(order);
    return toResult(order);
  }

  /** 审核通过进入硬预占（≥1 万走 REVIEW_FREEZE 双人复核）。 */
  @Transactional
  public SettlementResult auditPass(
      long claimId, String operatorId, List<String> reviewerIds, String firstReviewerId) {
    SettlementOrder order = requireMutable(claimId);
    order.auditPass(operatorId, reviewerIds, firstReviewerId);
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
        ctx.insuredIdHash(),
        ctx.policyNo(),
        ctx.planId(),
        claimId,
        order.getClaimNo(),
        amount,
        order.getSettlementNo(),
        attempt);

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
        ctx.insuredIdHash(),
        ctx.policyNo(),
        ctx.planId(),
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
        ctx.insuredIdHash(),
        ctx.policyNo(),
        ctx.planId(),
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
