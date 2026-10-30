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

  // ===================== 责任账（LIABILITY_CAP，按 liability_uuid 分行） =====================
  //
  // 为什么必须有第二本账：PERSONAL 记的是"这个人今年总共能赔多少"，LIABILITY_CAP 记的是"这项责任今年能赔多少"。
  // 只占 PERSONAL 的话，责任限额就是责任配置里的一个常量——跨案各用一次满额，直接系统性超赔。
  // 两本账共用同一张 t_quota_account、同一套三值模型与守恒校验，不另立表（多一本表就多一次对账）。

  /**
   * 责任账软占 + 免赔消抵累计（A7 建结算单时调用，与 {@link #reserve} 分属两个事务： 一次只写一个账户，守住 CORE-06 一事务一聚合）。
   *
   * @param quotaYear 额度年度
   * @param liabilityUuid 责任 UUID（分行键）
   * @param amount 本次占用（= 理算实付）
   * @param deductibleApplied 本次实际消抵的免赔（理算产出；回写后次案才只扣剩余免赔）
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reserveLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt) {
    QuotaAccount account =
        loadLiability(tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid);
    boolean touched = false;
    String softKey = claimNo + ":LIABILITY_SOFT:" + attempt;
    if (!alreadyOccupied(account, softKey) && amount != null && amount.signum() > 0) {
      account.reserveSoft(
          DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, softKey);
      touched = true;
    }
    // 免赔消抵与额度占用分开落两条流水：二者幂等周期不同（重算改金额、但免赔不一定改），
    // 合成一条会让"重算后免赔没回退/回退两次"这类差异无法从流水里查出来。
    String dedKey = claimNo + ":DEDUCTIBLE:" + attempt;
    if (!alreadyOccupied(account, dedKey)) {
      account.accumulateDeductible(
          DistributedIdGenerator.generateLongId(),
          deductibleApplied,
          claimId,
          settlementNo,
          dedKey);
      touched = true;
    }
    if (touched) {
      quotaAccountRepository.save(account);
    }
  }

  /** 责任账确认支付（PAID）：{@code frozen → claimed}。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void confirmLiabilityPay(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account =
        loadLiability(tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid);
    String key = claimNo + ":LIABILITY_PAID:" + attempt;
    if (alreadyOccupied(account, key)) {
      return;
    }
    if (amount == null || amount.signum() <= 0) {
      return;
    }
    account.confirm(DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  /**
   * 释放责任账预占（撤销 / 拒赔 / 支付失败）：{@code frozen} 归还，<b>并且已消抵的免赔必须回退</b>。
   *
   * <p>只还额度不还免赔，会出现"案件撤销了但免赔被吃掉"——客户下一案仍要重新消化一笔根本没赔出去的免赔。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void releaseLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt) {
    QuotaAccount account =
        loadLiability(tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid);
    boolean touched = false;
    String relKey = claimNo + ":LIABILITY_RELEASE:" + attempt;
    if (!alreadyOccupied(account, relKey) && amount != null && amount.signum() > 0) {
      account.release(
          DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, relKey);
      touched = true;
    }
    String dedKey = claimNo + ":DEDUCTIBLE_ROLLBACK:" + attempt;
    if (!alreadyOccupied(account, dedKey)) {
      account.reduceDeductible(
          DistributedIdGenerator.generateLongId(),
          deductibleApplied,
          claimId,
          settlementNo,
          dedKey);
      touched = true;
    }
    if (touched) {
      quotaAccountRepository.save(account);
    }
  }

  /** 责任账退款回冲（REFUNDED）：{@code claimed} 归还 + 免赔回退。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void refundLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt) {
    QuotaAccount account =
        loadLiability(tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid);
    boolean touched = false;
    String refKey = claimNo + ":LIABILITY_REFUND:" + attempt;
    if (!alreadyOccupied(account, refKey) && amount != null && amount.signum() > 0) {
      account.rollback(
          DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, refKey);
      touched = true;
    }
    String dedKey = claimNo + ":DEDUCTIBLE_ROLLBACK:" + attempt;
    if (!alreadyOccupied(account, dedKey)) {
      account.reduceDeductible(
          DistributedIdGenerator.generateLongId(),
          deductibleApplied,
          claimId,
          settlementNo,
          dedKey);
      touched = true;
    }
    if (touched) {
      quotaAccountRepository.save(account);
    }
  }

  private QuotaAccount loadLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid) {
    QuotaAccount account =
        quotaAccountRepository.findAccount(
            insuredIdHash,
            policyNo,
            planId,
            quotaYear,
            "LIABILITY_CAP",
            liabilityUuid == null ? "" : liabilityUuid);
    if (account == null) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, policyNo + "/" + liabilityUuid + "@" + quotaYear);
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
