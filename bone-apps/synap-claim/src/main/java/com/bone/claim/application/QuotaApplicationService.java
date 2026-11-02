package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.settlement.FundingStage;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A5 额度账户应用服务（写侧）——<b>个人账（PERSONAL）</b>一组用例 + 责任账委派入口。
 *
 * <p>每个方法只写 {@code QuotaAccount} 一个聚合（CORE-06 一事务一聚合）。
 *
 * <p><b>类边界（2026-11-02 拆分）</b>：原 849 行同含个人账与责任账两组方法，现按限界上下文拆为三个构件—— 本类（个人账 + 事务边界 + 对外 API
 * 不变）、{@link LiabilityQuotaService}（责任账逻辑）、{@link QuotaLedgerSupport} （锁条带 + 流水视图 +
 * 自有量归属推导，两本账共用<b>同一份</b>实现）。拆分的硬约束是：<b>锁与归属口径不能各持一份</b>， 否则同一账户在两本账上映射到不同锁、归属判定两套口径，正确性会静默退化。
 *
 * <p><b>传播语义：{@link Propagation#REQUIRED}（加入调用方事务），而不是 {@code REQUIRES_NEW}。</b>
 *
 * <p>为什么改（2026-10-31，资金 P0 修复）：一笔结算的额度占用横跨<b>两本账</b>（个人账 PERSONAL + 责任账
 * LIABILITY_CAP），而结算单本身是第三个聚合。此前两本账各用 {@code REQUIRES_NEW} 独立事务， 于是"个人账释放已提交 → 责任账释放抛错 → 外层结算事务回滚"
 * ⇒ <b>个人账已解冻却不回滚、结算单仍卡在冻结态</b>：账实不符 + 结算单永久卡死（再取消必撞同一错）。 同一形态的洞还有三处：建结算单（个人软占已提交、责任软占失败 ⇒
 * 留下无对应结算单的幽灵预占）、 支付（个人账已扣、结算单未付）、退款（同上）。
 *
 * <p>改为 {@code REQUIRED} 后，<b>「结算单状态 + 两本账」在同一事务内要么全成、要么全败</b>。 这是一处<b>经登记的 CORE-06
 * 例外</b>（同库本地跨聚合事务）：业务不变量＝"结算单的额度占用在两本账上 同时存在、同时消失"；失败语义＝任一本账失败则全部回滚，结算单不推进；并发成本＝两本账按固定顺序 （个人 →
 * 责任）加锁，避免交叉死锁，账户用 {@code @Version} 乐观锁做冲突检测。 详见 {@code
 * doc/ai-product/synapClaim/09-records/impl-log/16-额度释放原子性与占用归属（P0-5）.md}。
 *
 * <p>幂等：{@code idempotencyKey = {claimNo}:{stage}:{attempt}}（§7.5），同键重复调用直接跳过（不二次占额）。 额度变更经 {@code
 * QuotaTransaction} 落流水，不另发领域事件（{@link NoDomainEvent}）。
 */
@Service
@NoDomainEvent
public class QuotaApplicationService {

  private final QuotaAccountRepository quotaAccountRepository;
  private final QuotaLedgerSupport support;
  private final LiabilityQuotaService liability;

  /**
   * 单构造器：Spring 自动装配 {@code QuotaAccountRepository}，同时把<b>同一个</b> {@link QuotaLedgerSupport}
   * 实例交给责任账， 保证两本账共用一组锁条带与一份归属推导。
   */
  public QuotaApplicationService(QuotaAccountRepository quotaAccountRepository) {
    this.quotaAccountRepository = quotaAccountRepository;
    this.support = new QuotaLedgerSupport(quotaAccountRepository);
    this.liability = new LiabilityQuotaService(quotaAccountRepository, support);
  }

  /**
   * 软占（SOFT_RESERVED）：理算已算未确认的额度预占，计入 {@code frozen}。
   *
   * @param quotaYear 额度年度（由 {@code QuotaYearResolver} 单点裁决；年度是账户行键的一部分， 不在本类就地取"当前年"——那会让读写两侧各算一次）
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public void reserve(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    String ak =
        QuotaLedgerSupport.accountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, "PERSONAL", "");
    support.withAccountLock(
        ak,
        () ->
            doReserve(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                claimId,
                claimNo,
                amount,
                settlementNo,
                attempt));
  }

  private void doReserve(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId, quotaYear);
    String key = claimNo + ":" + FundingStage.SOFT_RESERVED.name() + ":" + attempt;
    if (support.alreadyOccupied(account, key)) {
      return;
    }
    account.reserveSoft(
        DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  /** 确认支付（PAID）：{@code frozen → claim} 同额转移，不二次占用。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public void confirmPay(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    String ak =
        QuotaLedgerSupport.accountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, "PERSONAL", "");
    support.withAccountLock(
        ak,
        () ->
            doConfirmPay(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                claimId,
                claimNo,
                amount,
                settlementNo,
                attempt));
  }

  private void doConfirmPay(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId, quotaYear);
    String key = claimNo + ":" + FundingStage.PAID.name() + ":" + attempt;
    if (support.alreadyOccupied(account, key)) {
      return;
    }
    account.confirm(DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  /**
   * 释放预占（RELEASED/FAILED）：撤销/拒赔/支付失败回冲。
   *
   * <p><b>只归还本单自有预占</b>（见 {@link QuotaLedgerSupport#ownHeld}）：共享池里还压着别的结算单的预占，
   * 按池余额释放会让本单吃掉他人的预占（越权释放 ⇒ 他人合法释放被拒、幽灵冻结）。
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public void release(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    String ak =
        QuotaLedgerSupport.accountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, "PERSONAL", "");
    support.withAccountLock(
        ak,
        () ->
            doRelease(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                claimId,
                claimNo,
                amount,
                settlementNo,
                attempt));
  }

  private void doRelease(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId, quotaYear);
    String key = claimNo + ":RELEASE:" + attempt;
    if (support.alreadyOccupied(account, key)) {
      return;
    }
    BigDecimal held = support.ownHeld(account, settlementNo);
    if (held.signum() > 0 && held.compareTo(amount) >= 0) {
      // 本单自有占用足额 ⇒ 释放合法；若共享池投影已被抽干，先按流水自愈（否则合法释放会被池级余额拒掉）
      support.reconcileProjectionIfDeficit(account, amount);
    }
    account.releaseOwned(
        DistributedIdGenerator.generateLongId(), amount, held, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  /** 退款（REFUNDED）：已付退票，红冲发票并回冲 {@code claimed}。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public void refund(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    String ak =
        QuotaLedgerSupport.accountKey(
            tenantId, insuredIdHash, policyNo, planId, quotaYear, "PERSONAL", "");
    support.withAccountLock(
        ak,
        () ->
            doRefund(
                tenantId,
                insuredIdHash,
                policyNo,
                planId,
                quotaYear,
                claimId,
                claimNo,
                amount,
                settlementNo,
                attempt));
  }

  private void doRefund(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      long claimId,
      String claimNo,
      BigDecimal amount,
      String settlementNo,
      int attempt) {
    QuotaAccount account = loadPersonal(tenantId, insuredIdHash, policyNo, planId, quotaYear);
    String key = claimNo + ":REFUND:" + attempt;
    if (support.alreadyOccupied(account, key)) {
      return;
    }
    account.rollback(DistributedIdGenerator.generateLongId(), amount, claimId, settlementNo, key);
    quotaAccountRepository.save(account);
  }

  private QuotaAccount loadPersonal(
      long tenantId, String insuredIdHash, String policyNo, long planId, int quotaYear) {
    QuotaAccount account =
        quotaAccountRepository.findAccount(
            insuredIdHash, policyNo, planId, quotaYear, "PERSONAL", "");
    if (account == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, policyNo);
    }
    return account;
  }

  // ===================== 责任账委派入口（LIABILITY_CAP，按 liability_uuid 分行） =====================
  //
  // 逻辑在 LiabilityQuotaService（不是 bean，事务开不出代理），故事务边界留在本类的公开方法上：
  // 调用方仍经本类进入，@Transactional(REQUIRED) 语义与拆分前完全一致（加入调用方事务，非 REQUIRES_NEW）。
  // 不要把这些方法改成直接注入 LiabilityQuotaService 调用——那会绕过事务，破坏「结算单 + 两本账」原子性。

  /** 责任账软占 + 免赔消抵累计（参数语义见 {@link LiabilityQuotaService#reserveLiability}）。 */
  @Transactional(propagation = Propagation.REQUIRED)
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
      int attempt,
      java.time.LocalDate businessDate) {
    liability.reserveLiability(
        tenantId,
        insuredIdHash,
        policyNo,
        planId,
        quotaYear,
        liabilityUuid,
        claimId,
        claimNo,
        amount,
        deductibleApplied,
        settlementNo,
        attempt,
        businessDate);
  }

  /** 责任账确认支付（PAID）：{@code frozen → claimed}。 */
  @Transactional(propagation = Propagation.REQUIRED)
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
    liability.confirmLiabilityPay(
        tenantId,
        insuredIdHash,
        policyNo,
        planId,
        quotaYear,
        liabilityUuid,
        claimId,
        claimNo,
        amount,
        settlementNo,
        attempt);
  }

  /** 释放责任账预占（撤销 / 拒赔 / 支付失败）：额度与免赔都只归还本单自有量。 */
  @Transactional(propagation = Propagation.REQUIRED)
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
    liability.releaseLiability(
        tenantId,
        insuredIdHash,
        policyNo,
        planId,
        quotaYear,
        liabilityUuid,
        claimId,
        claimNo,
        amount,
        deductibleApplied,
        settlementNo,
        attempt);
  }

  /** 责任账退款回冲（REFUNDED）：{@code claimed} 归还 + 免赔回退。 */
  @Transactional(propagation = Propagation.REQUIRED)
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
    liability.refundLiability(
        tenantId,
        insuredIdHash,
        policyNo,
        planId,
        quotaYear,
        liabilityUuid,
        claimId,
        claimNo,
        amount,
        deductibleApplied,
        settlementNo,
        attempt);
  }
}
