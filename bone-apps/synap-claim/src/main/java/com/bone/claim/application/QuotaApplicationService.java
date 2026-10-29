package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.settlement.FundingStage;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A5 额度账户应用服务（写侧）。
 *
 * <p>每个方法只写 {@code QuotaAccount} 一个聚合（CORE-06 一事务一聚合），且以 {@code REQUIRES_NEW}
 * 独立事务运行——资金流转（A7）跨聚合改额度时调用本服务，保证"一事务一聚合"且额度守恒写路径不被并入结算事务。
 *
 * <p>幂等：{@code idempotencyKey = {claimNo}:{stage}:{attempt}}（§7.5），同键重复调用直接跳过（不二次占额）。 额度变更经 {@code
 * QuotaTransaction} 落流水，不另发领域事件（{@link NoDomainEvent}）。
 */
@Service
@NoDomainEvent
@RequiredArgsConstructor
public class QuotaApplicationService {

  private final QuotaAccountRepository quotaAccountRepository;

  /** 软占（SOFT_RESERVED）：理算已算未确认的额度预占，计入 {@code frozen}。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reserve(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId);
    String key = claimNo + ":" + FundingStage.SOFT_RESERVED.name() + ":" + attempt;
    if (alreadyOccupied(account, key)) {
      return;
    }
    account.reserveSoft(
        DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  /** 确认支付（PAID）：{@code frozen → claim} 同额转移，不二次占用。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void confirmPay(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId);
    String key = claimNo + ":" + FundingStage.PAID.name() + ":" + attempt;
    if (alreadyOccupied(account, key)) {
      return;
    }
    account.confirm(DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  /** 释放预占（RELEASED/FAILED）：撤销/拒赔/支付失败回冲。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void release(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId);
    String key = claimNo + ":RELEASE:" + attempt;
    if (alreadyOccupied(account, key)) {
      return;
    }
    account.release(DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  /** 退款（REFUNDED）：已付退票，红冲发票并回冲 {@code claimed}。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void refund(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId);
    String key = claimNo + ":REFUND:" + attempt;
    if (alreadyOccupied(account, key)) {
      return;
    }
    account.rollback(DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  private QuotaAccount loadPersonal(
      long tenantId, String insuredIdHash, String policyNo, long planId) {
    QuotaAccount account =
        quotaAccountRepository.findAccount(
            insuredIdHash, policyNo, planId, Year.now().getValue(), "PERSONAL", "");
    if (account == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, policyNo);
    }
    return account;
  }

  private boolean alreadyOccupied(QuotaAccount account, String key) {
    List<?> txns = quotaAccountRepository.findTransactions(account.getId());
    return txns.stream()
        .anyMatch(
            t ->
                key.equals(
                    ((com.bone.claim.domain.model.quota.QuotaTransaction) t).getIdempotencyKey()));
  }
}
