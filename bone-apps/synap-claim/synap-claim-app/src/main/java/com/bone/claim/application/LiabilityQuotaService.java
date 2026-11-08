package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;

/**
 * 责任账（按 {@code liability_uuid} 或共享池共保代码分行）写侧。
 *
 * <p>从 {@link QuotaApplicationService} 按限界上下文拆出：个人账回答「这个人今年总共能赔多少」，责任账回答「这项责任今年能赔多少」，
 * 两组用例的入参、幂等键、归属口径都不同，混在一个类里（原 849 行）任一改动都要通读另一半。
 *
 * <p><b>为什么必须有第二本账</b>：只占 {@code PERSONAL} 的话，责任限额就是责任配置里的一个常量——跨案各用一次满额，直接系统性超赔。 两本账共用同一张 {@code
 * t_quota_account}、同一套三值模型与守恒校验，不另立表（多一本表就多一次对账）。
 *
 * <p><b>共享池（共保代码）</b>：责任带 {@code sharedCode} 时，其责任账走 {@code SHARED_POOL} 维度（共保代码为分行键， 复用 {@code
 * liability_uuid} 列），同组多个责任共用同一额度池。账户键由 {@link QuotaAccount#capQuotaType} / {@link
 * QuotaAccount#capSubKey} 推导——写侧（本类）与读侧（{@code QuotaDrivenAccumulationPortAdapter}）必须同源， 否则读侧读成「已用
 * 0」而超赔。
 *
 * <p><b>事务边界不在本类</b>：本类不是 Spring bean，方法上的 {@code @Transactional} 不会被代理。事务由调用方 {@link
 * QuotaApplicationService} 的公开方法（{@code Propagation.REQUIRED}）开启——那正是「结算单 + 两本账同事务」的
 * 边界，不能下移到本类（下移只会变成自调用、注解失效）。<b>不要把本类注册为 bean 后直接注入调用</b>，那会绕开事务与 {@code REQUIRED} 的原子性约定。
 *
 * <p><b>幂等</b>：责任账四个动作的幂等键后缀各自独立（{@code LIABILITY_SOFT} / {@code LIABILITY_PAID} / {@code
 * LIABILITY_RELEASE} / {@code LIABILITY_REFUND}），与免赔、日用量三条流水分开记——它们的幂等周期不同
 * （重算改金额、但免赔不一定改），合成一条会让"重算后免赔没回退/回退两次"这类差异无法从流水里查出来。
 *
 * <p>{@link NoDomainEvent}：额度变更经 {@code QuotaTransaction} 落流水，不另发领域事件（与 {@link
 * QuotaApplicationService} 一致， 也是 ArchUnit E-5.4「save() 必须配 publishFrom() 或声明 @NoDomainEvent」的要求）。
 */
@NoDomainEvent
final class LiabilityQuotaService {

  private final QuotaAccountRepository quotaAccountRepository;
  private final QuotaLedgerSupport support;

  LiabilityQuotaService(QuotaAccountRepository quotaAccountRepository, QuotaLedgerSupport support) {
    this.quotaAccountRepository = quotaAccountRepository;
    this.support = support;
  }

  /**
   * 责任账软占 + 免赔消抵累计（A7 建结算单时调用，与个人账软占分属两个事务： 一次只写一个账户，守住 CORE-06 一事务一聚合）。
   *
   * @param quotaYear 额度年度
   * @param liabilityUuid 责任 UUID（独立责任的责任账分行键；共享责任时让位于 {@code sharedCode}）
   * @param sharedCode 共享池代码（空=独立责任走 {@code LIABILITY_CAP}；非空走 {@code SHARED_POOL}，按共保代码分行）
   * @param amount 本次占用（= 理算实付）
   * @param deductibleApplied 本次实际消抵的免赔（理算产出；回写后次案才只扣剩余免赔）
   * @param businessDate 本次的业务日（Q-05 日限额累计键：发票日期 / 赔案受理日；{@code null} = 本案不配日限额， 此时不落按日流水，而非记成"某天已用
   *     0"）
   */
  void reserveLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt,
      java.time.LocalDate businessDate) {
    String ak =
        capAccountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    support.withAccountLock(
        ak,
        () ->
            doReserveLiability(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                liabilityUuid,
                sharedCode,
                claimId,
                claimNo,
                amount,
                deductibleApplied,
                settlementNo,
                attempt,
                businessDate));
  }

  private void doReserveLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt,
      java.time.LocalDate businessDate) {
    QuotaAccount account =
        loadLiability(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    boolean touched = false;
    String softKey = claimNo + ":LIABILITY_SOFT:" + attempt;
    if (!support.alreadyOccupied(account, softKey) && amount != null && amount.signum() > 0) {
      account.reserveSoft(
          DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, softKey);
      touched = true;
    }
    // 免赔消抵与额度占用分开落两条流水：二者幂等周期不同（重算改金额、但免赔不一定改），
    // 合成一条会让"重算后免赔没回退/回退两次"这类差异无法从流水里查出来。
    String dedKey = claimNo + ":DEDUCTIBLE:" + attempt;
    if (!support.alreadyOccupied(account, dedKey)) {
      account.accumulateDeductible(
          DistributedIdGenerator.generateLongId(),
          deductibleApplied,
          claimId,
          settlementNo,
          dedKey);
      touched = true;
    }
    // 日用量（Q-05）与额度/免赔分开落第三条流水：它不改变任何余额，只回答"这一天的累计赔了多少"。
    // 幂等周期与软占一致（同一 attempt），但键独立——分账后"重算改金额"能只重记其中一条。
    String dailyKey = claimNo + ":DAILY:" + attempt;
    if (!support.alreadyOccupied(account, dailyKey) && businessDate != null) {
      QuotaTransaction daily =
          account.accumulateDaily(
              DistributedIdGenerator.generateLongId(),
              amount,
              businessDate,
              claimId,
              settlementNo,
              dailyKey);
      touched = touched || daily != null;
    }
    if (touched) {
      quotaAccountRepository.save(account);
    }
  }

  /** 责任账确认支付（PAID）：{@code frozen → claimed}。 */
  void confirmLiabilityPay(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    String ak =
        capAccountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    support.withAccountLock(
        ak,
        () ->
            doConfirmLiabilityPay(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                liabilityUuid,
                sharedCode,
                claimId,
                claimNo,
                amount,
                settlementNo,
                attempt));
  }

  private void doConfirmLiabilityPay(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account =
        loadLiability(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    String key = claimNo + ":LIABILITY_PAID:" + attempt;
    if (support.alreadyOccupied(account, key)) {
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
   *
   * <p><b>额度与免赔都只归还本单自有量</b>（见 {@link QuotaLedgerSupport#ownHeld} / {@link
   * QuotaLedgerSupport#ownDeductible}）：责任账是 多个案件<b>共享</b>的池，按池余额释放会让本单吃掉他人的预占/免赔（越权释放）。
   */
  void releaseLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt) {
    String ak =
        capAccountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    support.withAccountLock(
        ak,
        () ->
            doReleaseLiability(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                liabilityUuid,
                sharedCode,
                claimId,
                claimNo,
                amount,
                deductibleApplied,
                settlementNo,
                attempt));
  }

  private void doReleaseLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt) {
    QuotaAccount account =
        loadLiability(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    boolean touched = false;
    String relKey = claimNo + ":LIABILITY_RELEASE:" + attempt;
    if (!support.alreadyOccupied(account, relKey) && amount != null && amount.signum() > 0) {
      BigDecimal held = support.ownHeld(account, settlementNo);
      if (held.signum() > 0 && held.compareTo(amount) >= 0) {
        // 本单自有占用足额 ⇒ 释放合法；责任账是共享池，投影被他人越权释放抽干时先按流水自愈
        support.reconcileProjectionIfDeficit(account, amount);
      }
      QuotaTransaction released =
          account.releaseOwned(
              DistributedIdGenerator.generateLongId(), amount, held, claimId, settlementNo, relKey);
      touched = released != null;
    }
    String dedKey = claimNo + ":DEDUCTIBLE_ROLLBACK:" + attempt;
    if (!support.alreadyOccupied(account, dedKey)) {
      QuotaTransaction rolledBack =
          account.reduceDeductibleOwned(
              DistributedIdGenerator.generateLongId(),
              deductibleApplied,
              support.ownDeductible(account, settlementNo),
              claimId,
              settlementNo,
              dedKey);
      touched = touched || rolledBack != null;
    }
    // 日用量同步回退：不回退则该日额度被一笔已撤销的赔案永久吃掉，当天后续真实赔案被限额拦下（少赔）。
    String dailyKey = claimNo + ":DAILY_ROLLBACK:" + attempt;
    if (!support.alreadyOccupied(account, dailyKey)) {
      QuotaLedgerSupport.DailyOwned owned = support.ownDaily(account, settlementNo);
      QuotaTransaction rolledBack =
          account.reduceDaily(
              DistributedIdGenerator.generateLongId(),
              owned.amount(),
              owned.bizDate(),
              claimId,
              settlementNo,
              dailyKey);
      touched = touched || rolledBack != null;
    }
    if (touched) {
      quotaAccountRepository.save(account);
    }
  }

  /** 责任账退款回冲（REFUNDED）：{@code claimed} 归还 + 免赔回退。 */
  void refundLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt) {
    String ak =
        capAccountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    support.withAccountLock(
        ak,
        () ->
            doRefundLiability(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                liabilityUuid,
                sharedCode,
                claimId,
                claimNo,
                amount,
                deductibleApplied,
                settlementNo,
                attempt));
  }

  private void doRefundLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode,
      long claimId,
      String claimNo,
      BigDecimal amount,
      BigDecimal deductibleApplied,
      String settlementNo,
      int attempt) {
    QuotaAccount account =
        loadLiability(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid, sharedCode);
    boolean touched = false;
    String refKey = claimNo + ":LIABILITY_REFUND:" + attempt;
    if (!support.alreadyOccupied(account, refKey) && amount != null && amount.signum() > 0) {
      account.rollback(
          DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, refKey);
      touched = true;
    }
    String dedKey = claimNo + ":DEDUCTIBLE_ROLLBACK:" + attempt;
    if (!support.alreadyOccupied(account, dedKey)) {
      account.reduceDeductible(
          DistributedIdGenerator.generateLongId(),
          deductibleApplied,
          claimId,
          settlementNo,
          dedKey);
      touched = true;
    }
    // 退款同样归还当日占用：钱退回去了，就不该再占着那天的日限额。
    String dailyKey = claimNo + ":DAILY_REFUND:" + attempt;
    if (!support.alreadyOccupied(account, dailyKey)) {
      QuotaLedgerSupport.DailyOwned owned = support.ownDaily(account, settlementNo);
      QuotaTransaction rolledBack =
          account.reduceDaily(
              DistributedIdGenerator.generateLongId(),
              owned.amount(),
              owned.bizDate(),
              claimId,
              settlementNo,
              dailyKey);
      touched = touched || rolledBack != null;
    }
    if (touched) {
      quotaAccountRepository.save(account);
    }
  }

  /**
   * 责任账键（与 {@code findAccount} 的 UK 同构）；共享责任走 {@code SHARED_POOL}+共保代码，否则 {@code
   * LIABILITY_CAP}+责任UUID。
   */
  private String capAccountKey(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode) {
    return QuotaLedgerSupport.accountKey(
        tenantId,
        insuredIdHash,
        policyNo,
        planId,
        quotaYear,
        QuotaAccount.capQuotaType(sharedCode),
        QuotaAccount.capSubKey(sharedCode, liabilityUuid));
  }

  private QuotaAccount loadLiability(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid,
      String sharedCode) {
    String quotaType = QuotaAccount.capQuotaType(sharedCode);
    String subKey = QuotaAccount.capSubKey(sharedCode, liabilityUuid);
    QuotaAccount account =
        quotaAccountRepository.findAccount(
            insuredIdHash, policyNo, planId, quotaYear, quotaType, subKey);
    if (account == null) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND,
          policyNo + "/" + liabilityUuid + "/" + sharedCode + "@" + quotaYear);
    }
    return account;
  }
}
