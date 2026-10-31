package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.model.settlement.FundingStage;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A5 额度账户应用服务（写侧）。
 *
 * <p>每个方法只写 {@code QuotaAccount} 一个聚合（CORE-06 一事务一聚合）。
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
@RequiredArgsConstructor
public class QuotaApplicationService {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private final QuotaAccountRepository quotaAccountRepository;

  /** 软占（SOFT_RESERVED）：理算已算未确认的额度预占，计入 {@code frozen}。 */
  @Transactional(propagation = Propagation.REQUIRED)
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
  @Transactional(propagation = Propagation.REQUIRED)
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

  /**
   * 释放预占（RELEASED/FAILED）：撤销/拒赔/支付失败回冲。
   *
   * <p><b>只归还本单自有预占</b>（见 {@link #ownHeld}）：共享池里还压着别的结算单的预占， 按池余额释放会让本单吃掉他人的预占（越权释放 ⇒
   * 他人合法释放被拒、幽灵冻结）。
   */
  @Transactional(propagation = Propagation.REQUIRED)
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
    BigDecimal held = ownHeld(account, settlementNo);
    if (held.signum() > 0 && held.compareTo(amount) >= 0) {
      // 本单自有占用足额 ⇒ 释放合法；若共享池投影已被抽干，先按流水自愈（否则合法释放会被池级余额拒掉）
      reconcileProjectionIfDeficit(account, amount);
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
   * @param businessDate 本次的业务日（Q-05 日限额累计键：发票日期 / 赔案受理日；{@code null} = 本案不配日限额， 此时不落按日流水，而非记成"某天已用
   *     0"）
   */
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
    // 日用量（Q-05）与额度/免赔分开落第三条流水：它不改变任何余额，只回答"这一天的累计赔了多少"。
    // 幂等周期与软占一致（同一 attempt），但键独立——分账后"重算改金额"能只重记其中一条。
    String dailyKey = claimNo + ":DAILY:" + attempt;
    if (!alreadyOccupied(account, dailyKey) && businessDate != null) {
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
   *
   * <p><b>额度与免赔都只归还本单自有量</b>（见 {@link #ownHeld} / {@link #ownDeductible}）：责任账是
   * 多个案件<b>共享</b>的池，按池余额释放会让本单吃掉他人的预占/免赔（越权释放）。
   */
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
    QuotaAccount account =
        loadLiability(tenantId, insuredIdHash, policyNo, planId, quotaYear, liabilityUuid);
    boolean touched = false;
    String relKey = claimNo + ":LIABILITY_RELEASE:" + attempt;
    if (!alreadyOccupied(account, relKey) && amount != null && amount.signum() > 0) {
      BigDecimal held = ownHeld(account, settlementNo);
      if (held.signum() > 0 && held.compareTo(amount) >= 0) {
        // 本单自有占用足额 ⇒ 释放合法；责任账是共享池，投影被他人越权释放抽干时先按流水自愈
        reconcileProjectionIfDeficit(account, amount);
      }
      QuotaTransaction released =
          account.releaseOwned(
              DistributedIdGenerator.generateLongId(), amount, held, claimId, settlementNo, relKey);
      touched = released != null;
    }
    String dedKey = claimNo + ":DEDUCTIBLE_ROLLBACK:" + attempt;
    if (!alreadyOccupied(account, dedKey)) {
      QuotaTransaction rolledBack =
          account.reduceDeductibleOwned(
              DistributedIdGenerator.generateLongId(),
              deductibleApplied,
              ownDeductible(account, settlementNo),
              claimId,
              settlementNo,
              dedKey);
      touched = touched || rolledBack != null;
    }
    // 日用量同步回退：不回退则该日额度被一笔已撤销的赔案永久吃掉，当天后续真实赔案被限额拦下（少赔）。
    String dailyKey = claimNo + ":DAILY_ROLLBACK:" + attempt;
    if (!alreadyOccupied(account, dailyKey)) {
      DailyOwned owned = ownDaily(account, settlementNo);
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
    // 退款同样归还当日占用：钱退回去了，就不该再占着那天的日限额。
    String dailyKey = claimNo + ":DAILY_REFUND:" + attempt;
    if (!alreadyOccupied(account, dailyKey)) {
      DailyOwned owned = ownDaily(account, settlementNo);
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

  /**
   * 账户的完整流水视图（已落库 + 本事务内存中新增，按幂等键去重）。
   *
   * <p>为什么要合并两处：账户与流水分开查询（流水量大，随聚合回填会拖垮读取），而本服务现在把两本账 放在<b>同一事务</b>里写——同一事务内刚 append 的流水未必已对 {@code
   * findTransactions} 的查询可见。 只看 DB 会漏掉"本次事务刚记的流水"⇒ 归属推导算错；只看内存会漏掉历史流水。合并去重后两侧都全。
   */
  private List<QuotaTransaction> ledger(QuotaAccount account) {
    Map<String, QuotaTransaction> byKey = new LinkedHashMap<>();
    for (QuotaTransaction t : quotaAccountRepository.findTransactions(account.getId())) {
      byKey.put(t.getIdempotencyKey(), t);
    }
    List<QuotaTransaction> inMemory = account.getTransactions();
    if (inMemory != null) {
      for (QuotaTransaction t : inMemory) {
        byKey.putIfAbsent(t.getIdempotencyKey(), t);
      }
    }
    return new ArrayList<>(byKey.values());
  }

  /**
   * 本结算单在本账户上的<b>自有冻结占用额</b>（从流水推导）。
   *
   * <p>口径：{@code SOFT} 新增预占；{@code PAID} 把冻结转成已扣减（不再占冻结）；{@code RELEASE} 归还冻结。 {@code HARD}
   * 不新增占用（新版升级硬冻时 before==after，增量为 0）、{@code DEDUCTIBLE} 不占冻结、{@code REFUND} 回冲的是已扣减 ⇒ 三者均不计入。
   *
   * <p>这是"释放只能还自己的钱"的依据：共享池的 {@code frozen} 里混着多张结算单的预占， 只按池余额释放会越权吃掉他人预占。
   *
   * <p><b>老版本 HARD 的兼容</b>：早期版本用 {@code HARD} 直接起手占用（before≠after，真实增加了冻结额），与新版"SOFT 起手 + HARD
   * 升级"不同。若一律按"增量 0"处理，那些老单的自有占用会被算成 0 ⇒ 释放被当成空操作、其占用永远留在池里。 故 {@code HARD} 取 {@code max(0, after −
   * before)}：新版为 0（不计），老版为真实占用（计入）。
   *
   * <p>本口径与 {@link #projectFromLedger}（投影重算）<b>必须一致</b>，否则单笔归属判定与池级投影会互相打架。
   */
  private BigDecimal ownHeld(QuotaAccount account, String settlementNo) {
    BigDecimal held = ZERO;
    for (QuotaTransaction t : ledger(account)) {
      if (!settlementNo.equals(t.getSettlementNo())) {
        continue;
      }
      String type = t.getTxnType();
      if ("SOFT".equals(type)) {
        held = held.add(t.getAmount());
      } else if ("HARD".equals(type)) {
        held = held.add(hardDelta(t));
      } else if ("PAID".equals(type) || "RELEASE".equals(type)) {
        held = held.subtract(t.getAmount());
      }
    }
    return held.max(ZERO);
  }

  /**
   * {@code HARD} 流水的真实占用增量（{@code max(0, after − before)}）。
   *
   * <p>新版"SOFT 起手 + HARD 升级"下 HARD 的 before==after ⇒ 增量为 0（不重复计占用）； 早期版本用 HARD 直接起手时 before≠after ⇒
   * 增量为真实占用额。 {@code before}/{@code after} 缺失时按 0 处理（宁可少算也不凭空计占用）。
   */
  private BigDecimal hardDelta(QuotaTransaction t) {
    BigDecimal before = t.getBeforeAmount();
    BigDecimal after = t.getAfterAmount();
    if (before == null || after == null) {
      return ZERO;
    }
    return after.subtract(before).max(ZERO);
  }

  /**
   * 按流水事实<b>重算账户投影</b>（自愈：投影失真时修正投影，而不是阻塞业务）。
   *
   * <p>触发条件：<b>账户上的冻结额不足以支撑本次释放</b>。这说明投影与事实失真——最常见成因是历史越权释放（别的单据把共享池抽干）。
   * 此时正确动作是<b>按流水重算</b>，否则本单合法的释放会被永久拒掉、结算单卡死在冻结态、钱出不来。
   *
   * <p>重算口径与 {@link #ownHeld} 同源，但按结算单聚合后取"净占用为正"的单求和：越权释放（无对应占用的 RELEASE）净额为负， 被 {@code max(0, …)}
   * 剔除 ⇒ 投影回到事实（该单从未占用过，不应影响池）。
   *
   * <p>正常路径（投影充足）零开销：只做一次 O(1) 比较。
   */
  private void reconcileProjectionIfDeficit(QuotaAccount account, BigDecimal amount) {
    BigDecimal frozen = account.getQuotaFrozen();
    if (frozen != null && frozen.compareTo(amount) >= 0) {
      return; // 投影充足：正常路径，不重算
    }
    Projection projection = projectFromLedger(account);
    account.reconcileProjection(projection.frozen(), projection.claimed(), projection.deductible());
  }

  /**
   * 按流水聚合出的账户投影。
   *
   * @param frozen 冻结占用投影（Σ 各结算单净冻结占用，只计正数）
   * @param claimed 已扣减投影（Σ 各结算单净已付，只计正数）
   * @param deductible 年免赔已用投影（Σ 各结算单净消抵，只计正数）
   */
  private record Projection(BigDecimal frozen, BigDecimal claimed, BigDecimal deductible) {}

  /** 按流水事实聚合账户投影（见 {@link #reconcileProjectionIfDeficit} 的口径说明）。 */
  private Projection projectFromLedger(QuotaAccount account) {
    Map<String, BigDecimal> held = new LinkedHashMap<>();
    Map<String, BigDecimal> paid = new LinkedHashMap<>();
    Map<String, BigDecimal> deductible = new LinkedHashMap<>();
    for (QuotaTransaction t : ledger(account)) {
      String sn = t.getSettlementNo();
      if (sn == null) {
        continue;
      }
      String type = t.getTxnType();
      if ("SOFT".equals(type)) {
        held.merge(sn, t.getAmount(), BigDecimal::add);
      } else if ("HARD".equals(type)) {
        held.merge(sn, hardDelta(t), BigDecimal::add);
      } else if ("PAID".equals(type)) {
        held.merge(sn, t.getAmount().negate(), BigDecimal::add);
        paid.merge(sn, t.getAmount(), BigDecimal::add);
      } else if ("RELEASE".equals(type)) {
        held.merge(sn, t.getAmount().negate(), BigDecimal::add);
      } else if ("REFUND".equals(type)) {
        paid.merge(sn, t.getAmount().negate(), BigDecimal::add);
      } else if ("DEDUCTIBLE".equals(type)) {
        BigDecimal delta =
            "OCCUPY".equals(t.getDirection()) ? t.getAmount() : t.getAmount().negate();
        deductible.merge(sn, delta, BigDecimal::add);
      }
    }
    return new Projection(sumPositive(held), sumPositive(paid), sumPositive(deductible));
  }

  /** 各结算单净额求和（只计正数：净额为负 = 越权释放/无对应占用的归还，不应占用投影）。 */
  private BigDecimal sumPositive(Map<String, BigDecimal> perSettlement) {
    BigDecimal total = ZERO;
    for (BigDecimal net : perSettlement.values()) {
      if (net.signum() > 0) {
        total = total.add(net);
      }
    }
    return total;
  }

  /**
   * 本结算单在本账户上<b>自有免赔消抵额</b>（从流水推导）。
   *
   * <p>口径：{@code DEDUCTIBLE + OCCUPY} 是消抵，{@code DEDUCTIBLE + ROLLBACK} 是回退。
   * 年免赔是跨案共享池，只按池余额回退会把别人消抵掉的免赔退回来 ⇒ 客户下一案重复消化免赔（少赔）。
   */
  private BigDecimal ownDeductible(QuotaAccount account, String settlementNo) {
    BigDecimal owned = ZERO;
    for (QuotaTransaction t : ledger(account)) {
      if (!settlementNo.equals(t.getSettlementNo()) || !"DEDUCTIBLE".equals(t.getTxnType())) {
        continue;
      }
      if ("OCCUPY".equals(t.getDirection())) {
        owned = owned.add(t.getAmount());
      } else {
        owned = owned.subtract(t.getAmount());
      }
    }
    return owned.max(ZERO);
  }

  /**
   * 本结算单在某账户上的<b>自有日用量</b>（金额 + 业务日），从流水推导。
   *
   * <p>口径：{@code DAILY + OCCUPY} 是占用，{@code DAILY + ROLLBACK} 是回退。归属判定与免赔同构——日用量也是
   * <b>跨案共享</b>的一天额度，按"当天已用总额"回退会把别的赔案占的额度退出来 ⇒ 那天还能再赔一遍（超赔）。
   *
   * <p>业务日取本单占用行的 {@code biz_date}：回退流水必须落在同一天，否则读侧按日聚合时两边对不上， 退了个寂寞（占用还在）。
   */
  private DailyOwned ownDaily(QuotaAccount account, String settlementNo) {
    BigDecimal owned = ZERO;
    java.time.LocalDate bizDate = null;
    for (QuotaTransaction t : ledger(account)) {
      if (!settlementNo.equals(t.getSettlementNo()) || !"DAILY".equals(t.getTxnType())) {
        continue;
      }
      if ("OCCUPY".equals(t.getDirection())) {
        owned = owned.add(t.getAmount());
        if (bizDate == null) {
          bizDate = t.getBizDate();
        }
      } else {
        owned = owned.subtract(t.getAmount());
      }
    }
    return new DailyOwned(owned.max(ZERO), bizDate);
  }

  /** 自有日用量（金额 + 业务日）。业务日为 {@code null} 时不落回退流水（无法定位到某一天）。 */
  private record DailyOwned(BigDecimal amount, java.time.LocalDate bizDate) {}

  private boolean alreadyOccupied(QuotaAccount account, String key) {
    return ledger(account).stream().anyMatch(t -> key.equals(t.getIdempotencyKey()));
  }
}
