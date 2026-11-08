package com.bone.claim.application;

import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaProjection;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 额度账户写侧的<b>共享原语</b>：同账户串行锁、账户行键、流水视图与「自有量」归属推导。
 *
 * <p><b>为什么单独成类</b>：个人账（{@code PERSONAL}）与责任账（{@code LIABILITY_CAP}）是两本账、两组用例，
 * 但<b>归属推导必须只有一份实现</b>——{@code ownHeld}/{@code ownDeductible}/{@code ownDaily} 一旦两处各写一遍， 就会出现「个人账按
 * A 口径释放、责任账按 B 口径释放」的越权释放，且这种偏差在单测里看不出来（各自内部自洽）。 同理，锁条带必须<b>两本账共用同一组</b>：若各持一份 {@code
 * ReentrantLock[]}，同一账户在两本账上会映射到不同锁， 单副本内的串行化保证直接失效。
 *
 * <p><b>本类不是 Spring bean</b>，由 {@link QuotaApplicationService} 构造一次并同时交给 {@link
 * LiabilityQuotaService}， 保证「一个应用服务实例 = 一组锁 = 一份归属推导」。
 */
final class QuotaLedgerSupport {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  /**
   * 同账户额度扣减的<b>单写者串行队列</b>（striped lock）。
   *
   * <p><b>背景</b>：额度扣减是「读账户 → 改三值余额 → {@code save}（SDK 按 {@code @Version} 乐观锁 {@code WHERE id=? AND
   * version=?} 更新）」的高冲突写。多笔案件并发占同一被保人账户时，落后方会因 {@code OptimisticLockException} 回滚并重试， 在峰值下频繁撞「重试≤3
   * 次转人工」阈值（§5.3）——既损害自动化率，又制造"自动未占、人工又占"的竞态窗口（潜在超赔）。
   *
   * <p><b>本锁的作用</b>：在<b>单副本内</b>对同一账户串行化扣减，使同副本同时刻至多 1 笔在途，跨副本竞争退化为「副本数」并发——
   * 乐观锁几乎不再冲突、永不触达"转人工"阈值。跨副本正确性仍由 {@code @Version} 兜底（它本就正确，只是高并发下重试代价大）。
   *
   * <p><b>为何 striped 而非逐账户锁</b>：账户数可达千万级，逐账户 {@code ConcurrentHashMap} 会持续泄漏内存；固定 4096 把锁的
   * 条带化把内存收敛为常量，且<b>同一账户永远映射到同一把锁</b>（哈希稳定），正确性不因此损失——仅可能出现"不同账户共用一把锁"的
   * 额外串行（纯性能代价，可接受）。调用方对同一案件固定以「个人账 → 责任账」顺序进入（见 {@link QuotaApplicationService} 类注释 CORE-06
   * 例外），且每笔扣减只占一把账户锁、 不在方法间嵌套持有，故无死锁。
   *
   * <p><b>与多副本的边界</b>：本锁不跨 JVM，故<b>真正的多副本 DB 级并发正确性压测（golive.12 / G16）仍是最终验证</b>，本锁只是把
   * "单副本内同账户并发"这一最现实、最频繁的冲突源消掉。详见《工程落地版》§5.3 与 golive.12。
   */
  private static final int ACCOUNT_LOCK_STRIPES = 1 << 12; // 4096 条带，内存恒定

  private final QuotaAccountRepository quotaAccountRepository;
  private final ReentrantLock[] accountLockStripes = new ReentrantLock[ACCOUNT_LOCK_STRIPES];

  QuotaLedgerSupport(QuotaAccountRepository quotaAccountRepository) {
    this.quotaAccountRepository = quotaAccountRepository;
    for (int i = 0; i < ACCOUNT_LOCK_STRIPES; i++) {
      accountLockStripes[i] = new ReentrantLock();
    }
  }

  private ReentrantLock stripeFor(String key) {
    return accountLockStripes[(key.hashCode() & Integer.MAX_VALUE) % ACCOUNT_LOCK_STRIPES];
  }

  void withAccountLock(String key, Runnable action) {
    ReentrantLock lock = stripeFor(key);
    lock.lock();
    try {
      action.run();
    } finally {
      lock.unlock();
    }
  }

  /** 账户行键（与 {@code findAccount} 的 UK 同构），用作同账户串行化的锁标识。 */
  static String accountKey(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String quotaType,
      String liabilityUuid) {
    return tenantId
        + ":"
        + insuredIdHash
        + ":"
        + policyNo
        + ":"
        + planId
        + ":"
        + quotaYear
        + ":"
        + quotaType
        + ":"
        + (liabilityUuid == null ? "" : liabilityUuid);
  }

  /**
   * 账户的完整流水视图（已落库 + 本事务内存中新增，按幂等键去重）。
   *
   * <p>为什么要合并两处：账户与流水分开查询（流水量大，随聚合回填会拖垮读取），而本服务现在把两本账 放在<b>同一事务</b>里写——同一事务内刚 append 的流水未必已对 {@code
   * findTransactions} 的查询可见。 只看 DB 会漏掉"本次事务刚记的流水"⇒ 归属推导算错；只看内存会漏掉历史流水。合并去重后两侧都全。
   */
  List<QuotaTransaction> ledger(QuotaAccount account) {
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
   * <p>本口径与 {@link QuotaProjection#rebuild}（投影重算）<b>必须一致</b>，否则单笔归属判定与池级投影会互相打架： 故 {@code HARD}
   * 的增量算式取自 {@link QuotaProjection#hardDelta}，两处共用一份定义。
   */
  BigDecimal ownHeld(QuotaAccount account, String settlementNo) {
    BigDecimal held = ZERO;
    for (QuotaTransaction t : ledger(account)) {
      if (!settlementNo.equals(t.getSettlementNo())) {
        continue;
      }
      String type = t.getTxnType();
      if ("SOFT".equals(type)) {
        held = held.add(t.getAmount());
      } else if ("HARD".equals(type)) {
        held = held.add(QuotaProjection.hardDelta(t));
      } else if ("PAID".equals(type) || "RELEASE".equals(type)) {
        held = held.subtract(t.getAmount());
      }
    }
    return held.max(ZERO);
  }

  /**
   * 按流水事实<b>重算账户投影</b>（自愈：投影失真时修正投影，而不是阻塞业务）。
   *
   * <p>触发条件：<b>账户上的冻结额不足以支撑本次释放</b>。这说明投影与事实失真——最常见成因是历史越权释放（别的单据把共享池抽干）。
   * 此时正确动作是<b>按流水重算</b>，否则本单合法的释放会被永久拒掉、结算单卡死在冻结态、钱出不来。
   *
   * <p>重算口径在 {@link QuotaProjection#rebuild}（与对账共用同一实现）：按结算单聚合后取"净占用为正"的单求和，越权释放（无对应占用的
   * RELEASE）净额为负，被剔除 ⇒ 投影回到事实（该单从未占用过，不应影响池）。
   *
   * <p>正常路径（投影充足）零开销：只做一次 O(1) 比较。
   */
  void reconcileProjectionIfDeficit(QuotaAccount account, BigDecimal amount) {
    BigDecimal frozen = account.getQuotaFrozen();
    if (frozen != null && frozen.compareTo(amount) >= 0) {
      return; // 投影充足：正常路径，不重算
    }
    QuotaProjection projection = QuotaProjection.rebuild(ledger(account));
    account.reconcileProjection(
        projection.frozen(), projection.claimed(), projection.deductibleUsed());
  }

  /**
   * 本结算单在本账户上<b>自有免赔消抵额</b>（从流水推导）。
   *
   * <p>口径：{@code DEDUCTIBLE + OCCUPY} 是消抵，{@code DEDUCTIBLE + ROLLBACK} 是回退。
   * 年免赔是跨案共享池，只按池余额回退会把别人消抵掉的免赔退回来 ⇒ 客户下一案重复消化免赔（少赔）。
   */
  BigDecimal ownDeductible(QuotaAccount account, String settlementNo) {
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
  DailyOwned ownDaily(QuotaAccount account, String settlementNo) {
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
  record DailyOwned(BigDecimal amount, java.time.LocalDate bizDate) {}

  boolean alreadyOccupied(QuotaAccount account, String key) {
    return ledger(account).stream().anyMatch(t -> key.equals(t.getIdempotencyKey()));
  }
}
