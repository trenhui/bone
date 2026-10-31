package com.bone.claim.domain.model.quota;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 个人额度账户（A5 聚合根，三值模型）。
 *
 * <p><b>三值不可简化为两值</b>（§5.3）：{@code claimed}（已确认扣减）与 {@code frozen}（理算已算但未确认）
 * 互斥统计，因为"理算已算但核赔未确认"的钱尚未真正付出，却必须被占位——否则并发下会超赔：
 *
 * <pre>
 * 剩余 = initial − claimed
 * 可用 = initial − claimed − frozen
 * </pre>
 *
 * <p>守恒不变量 {@code available = initial − claimed − frozen} 由本类在每次状态变更后
 * **立即自校验**（写路径强制），对账（§11.7）只是侦测型安全网——金融系统不依赖批处理保证一致性。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_quota_account")
public class QuotaAccount extends TenantAggregateRoot<Long> {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  @Column(name = "insured_id_hash", nullable = false)
  private String insuredIdHash;

  @Column(name = "policy_no", nullable = false)
  private String policyNo;

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  /** 额度年度（按保单年度，非自然年）。 */
  @Column(name = "quota_year", nullable = false)
  private Integer quotaYear;

  /** 额度维度：PERSONAL 个人 / LIABILITY_CAP 责任额度 / LIABILITY_PERSONAL 责任个人额度（§5.3）。 */
  @Column(name = "quota_type", nullable = false)
  private String quotaType;

  /** 责任 UUID（LIABILITY_* 维度必填；PERSONAL 为空）。 */
  @Column(name = "liability_uuid")
  private String liabilityUuid;

  @Column(name = "quota_initial", nullable = false)
  private BigDecimal quotaInitial;

  /** 已扣减（理算已确认）。 */
  @Column(name = "quota_claimed", nullable = false)
  private BigDecimal quotaClaimed;

  /** 已冻结（理算未确认）。 */
  @Column(name = "quota_frozen", nullable = false)
  private BigDecimal quotaFrozen;

  /** 年免赔已用（R-DED-03 仅累计社保范围内自付一）。 */
  @Column(name = "deductible_used")
  private BigDecimal deductibleUsed;

  /** 年封顶已用（R-CAP）。 */
  @Column(name = "cap_used")
  private BigDecimal capUsed;

  /** 减人标记：0 正常 / 1 减人（减人不参与额度累计）。 */
  @Column(name = "insured_state")
  private Integer insuredState;

  /** 审计时间戳（显式声明：省略列会撞 {@code NOT NULL} 无默认值的 DDL）。 */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Transient
  @Cascade(foreignKey = "quotaAccountId", orphanRemoval = false)
  private List<QuotaTransaction> transactions = new ArrayList<>();

  @Version
  @Column(name = "version")
  private Long version;

  private QuotaAccount(
      Long id,
      Long tenantId,
      String insuredIdHash,
      String policyNo,
      Long planId,
      Integer quotaYear,
      String quotaType,
      String liabilityUuid,
      BigDecimal quotaInitial) {
    setId(id);
    setTenantId(tenantId);
    this.insuredIdHash = insuredIdHash;
    this.policyNo = policyNo;
    this.planId = planId;
    this.quotaYear = quotaYear;
    this.quotaType = quotaType;
    this.liabilityUuid = liabilityUuid == null ? "" : liabilityUuid;
    this.quotaInitial = quotaInitial;
    this.quotaClaimed = ZERO;
    this.quotaFrozen = ZERO;
    this.deductibleUsed = ZERO;
    this.capUsed = ZERO;
    this.insuredState = 0;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static QuotaAccount open(
      Long id,
      Long tenantId,
      String insuredIdHash,
      String policyNo,
      Long planId,
      Integer quotaYear,
      String quotaType,
      String liabilityUuid,
      BigDecimal quotaInitial) {
    if (quotaInitial == null || quotaInitial.signum() < 0) {
      throw new DomainException("初始额度不能为负");
    }
    if (quotaInitial.compareTo(new BigDecimal("100000000")) > 0) {
      throw new DomainException("初始额度超出上限，请核对配置");
    }
    return new QuotaAccount(
        id,
        tenantId,
        insuredIdHash,
        policyNo,
        planId,
        quotaYear,
        quotaType,
        liabilityUuid,
        quotaInitial.setScale(2, java.math.RoundingMode.HALF_UP));
  }

  /** 可用额度（真正的可赔额度）。 */
  public BigDecimal available() {
    return quotaInitial.subtract(quotaClaimed).subtract(quotaFrozen).max(ZERO);
  }

  /**
   * 软占（SOFT，§5.3：{@code SettlementOrder} 创建即冻结，计入 {@code frozen} 不扣 {@code claimed}）。
   *
   * <p>流水 {@code direction=OCCUPY / txn_type=SOFT}——<b>软占与硬冻必须是两种 txn_type</b>：流水是 append-only 的
   * 事实表（账户只是投影），若软占也记 {@code HARD}，下游按 txn_type 统计软/硬占用、以及对账"SOFT→HARD 阶段单调"
   * 都会失真，同一笔钱会在两个阶段各被计一次占用（双冻结）。
   *
   * @param amount 冻结金额
   * @return 本次流水（append-only）
   */
  public QuotaTransaction reserveSoft(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    requirePositive(amount, "冻结金额");
    if (available().compareTo(amount) < 0) {
      throw new DomainException("可用额度不足（initial−claimed−frozen 已耗尽）");
    }
    BigDecimal before = quotaFrozen;
    this.quotaFrozen = quotaFrozen.add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    assertConserved();
    return append(
        txnId,
        claimId,
        settlementNo,
        "OCCUPY",
        "SOFT",
        amount,
        before,
        quotaFrozen,
        idempotencyKey);
  }

  /**
   * 硬冻升级（HARD，§5.3：{@code HARD_RESERVED} 沿用软占已记的 {@code frozen}，<b>不新增占用</b>，仅记录硬预占状态）。
   *
   * <p>流水 {@code direction=OCCUPY / txn_type=HARD}，{@code before == after}（额度未变动）。<b>禁止</b>在此再
   * {@code frozen += amount}：那会让同一笔钱被软占与硬冻各计一次，直接造成"可用额度凭空蒸发"。
   *
   * @param amount 本次升级为硬冻的金额（不得超过已冻结额）
   * @return 本次流水（append-only）
   */
  public QuotaTransaction upgradeToHard(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    requirePositive(amount, "硬冻金额");
    if (quotaFrozen.compareTo(amount) < 0) {
      throw new DomainException("已冻结额度小于硬冻金额，硬冻升级不得新增占用");
    }
    return append(
        txnId,
        claimId,
        settlementNo,
        "OCCUPY",
        "HARD",
        amount,
        quotaFrozen,
        quotaFrozen,
        idempotencyKey);
  }

  /** 确认（核赔通过转支付：frozen → claimed 同额转移，不二次占用）。 */
  public QuotaTransaction confirm(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    requirePositive(amount, "确认金额");
    if (quotaFrozen.compareTo(amount) < 0) {
      throw new DomainException("冻结额度小于确认金额，无法同额转移");
    }
    BigDecimal before = quotaClaimed;
    this.quotaFrozen = quotaFrozen.subtract(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    this.quotaClaimed = quotaClaimed.add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    this.capUsed = capUsed.add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    assertConserved();
    return append(
        txnId,
        claimId,
        settlementNo,
        "OCCUPY",
        "PAID",
        amount,
        before,
        quotaClaimed,
        idempotencyKey);
  }

  /**
   * 释放冻结（重算/撤销：frozen 归还）。
   *
   * <p><b>注意</b>：本方法只校验"账户上的冻结额够不够"——那是<b>共享池</b>口径，池里还压着别的结算单的预占。 跨结算单的释放必须走 {@link
   * #releaseOwned}（按本单自有预占校验），否则 A 单释放会吃掉 B 单的预占。
   *
   * <p>本方法<b>生产代码零调用</b>（释放路径一律走 {@link #releaseOwned}），只作为"无归属信息"场景与测试夹具存在； 保留它是因为它携带的池级前置校验仍是
   * {@link #releaseHeld} 的安全网。
   */
  public QuotaTransaction release(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    requirePositive(amount, "释放金额");
    if (quotaFrozen.compareTo(amount) < 0) {
      throw new DomainException("冻结额度小于释放金额");
    }
    return releaseHeld(txnId, amount, claimId, settlementNo, idempotencyKey);
  }

  /**
   * 释放<b>本结算单自有</b>的冻结预占（归还 {@code frozen}）。
   *
   * <p>与 {@link #release} 只差一条判据，但这条判据是资金正确性的分水岭：{@code release} 只看
   * <b>共享池余额</b>（"账户上冻结额够不够"），而共享池里还压着别的结算单的预占 ⇒ A 单释放会吃掉 B 单的预占，池被抽干后 B 单的合法释放被拒（越权释放 / 幽灵冻结）。
   *
   * <p>{@code ownHeld} 是<b>本结算单在本账户上的自有冻结额</b>（由流水推导，见 {@code
   * QuotaApplicationService#ownHeld}），据此三态判定：
   *
   * <ul>
   *   <li>{@code ownHeld == 0}：本单在此账户上已无冻结占用（此前已释放，或从未在此占额） ⇒
   *       <b>空操作</b>：不落流水、不触碰共享池。这是幂等重入的正常形态（结算单已释放过一遍）。
   *   <li>{@code 0 < ownHeld < amount}：账实不符（本单持有量不足以释放本次金额） ⇒ 抛异常，<b>拒绝越权释放</b>（fail-closed）。
   *   <li>{@code ownHeld >= amount}：正常释放。
   * </ul>
   *
   * <p><b>归属校验通过后不再校验"共享池总额是否够"</b>（2026-10-31，F3 修复）：按业界 escrow/hold 语义， 释放一笔占用（hold）的唯一前置条件是"该
   * hold 存在且未被释放"，<b>账户 {@code frozen} 是投影 （Σ 各笔未结
   * hold），不是释放的前置门槛</b>。若池总额小于本单持有量，说明<b>投影已被他人越权释放抽干</b>， 那是别的单据的历史污染，正确动作是<b>按流水重算投影自愈</b>（{@link
   * #reconcileProjection}，由应用层在释放前触发）， 而不是把本单的合法释放永久拒掉——那会让结算单卡死在冻结态、钱永远出不来（线上实证：claim
   * 771522015306711040 自有 hold 足额 18000，却因池被抽干后只剩 3969.92 而永久无法取消）。
   *
   * <p>若应用层未先行重算、投影仍不足以支撑本次扣减，{@link #assertConserved} 会以"额度守恒被破坏" fail-closed。
   *
   * @param ownHeld 本单自有冻结额；{@code null} 视为 0（无自有预占 ⇒ 空操作）
   * @return 本次流水；空操作时返回 {@code null}
   */
  public QuotaTransaction releaseOwned(
      long txnId,
      BigDecimal amount,
      BigDecimal ownHeld,
      Long claimId,
      String settlementNo,
      String idempotencyKey) {
    requirePositive(amount, "释放金额");
    BigDecimal held = ownHeld == null ? ZERO : ownHeld;
    if (held.signum() <= 0) {
      return null; // 本单已无自有占用：空操作，绝不触碰共享池
    }
    if (held.compareTo(amount) < 0) {
      throw new DomainException("本单自有冻结占用 " + held + " 小于待释放 " + amount + "：额度账实不符，拒绝越权释放共享池额度");
    }
    return releaseHeld(txnId, amount, claimId, settlementNo, idempotencyKey);
  }

  /**
   * 归还冻结额并落 {@code RELEASE} 流水（<b>不含池级余额前置校验</b>）。
   *
   * <p>这是 {@link #release} 与 {@link #releaseOwned} 共用的落账路径：调用方必须已就"这笔钱是谁的"给出判据 （池级余额 or 本单自有
   * hold），本方法只负责改投影 + 记事实。
   *
   * <p>{@code frozen} 扣减后为负 ⇒ {@link #assertConserved} fail-closed（说明投影失真且调用方未自愈）。
   */
  private QuotaTransaction releaseHeld(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    BigDecimal before = quotaFrozen;
    this.quotaFrozen = quotaFrozen.subtract(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    assertConserved();
    return append(
        txnId,
        claimId,
        settlementNo,
        "ROLLBACK",
        "RELEASE",
        amount,
        before,
        quotaFrozen,
        idempotencyKey);
  }

  /**
   * 按流水事实<b>重算并覆盖账户投影</b>（自愈）。
   *
   * <p>账户的 {@code frozen}/{@code claimed}/{@code deductible_used}/{@code cap_used}
   * 是<b>投影</b>（各笔占用的汇总）， 流水才是事实。投影与事实失准时（历史越权释放、并发丢失更新、跨版本代码路径），<b>应当修正投影而不是阻塞业务</b>：
   * 阻塞会让本单合法的释放被拒、结算单永久卡死、钱出不来。
   *
   * <p>调用方（应用层）负责按流水算出正确的投影值并传入；本方法只负责落值 + 守恒自校验（守恒被破坏仍 fail-closed）。
   *
   * @param frozen 重算后的冻结占用投影
   * @param claimed 重算后的已扣减投影
   * @param deductibleUsed 重算后的年免赔已用投影
   */
  public void reconcileProjection(
      BigDecimal frozen, BigDecimal claimed, BigDecimal deductibleUsed) {
    this.quotaFrozen = scale(frozen);
    this.quotaClaimed = scale(claimed);
    this.capUsed = scale(claimed);
    this.deductibleUsed = scale(deductibleUsed);
    this.updatedAt = Instant.now();
    assertConserved();
  }

  private static BigDecimal scale(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      return ZERO;
    }
    return value.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  /**
   * 回冲已付（退款/红冲：claimed 归还）。
   *
   * <p><b>{@code txn_type=REFUND} 而非 RELEASE</b>：本方法回冲的是 {@code claimed}（已付钱）， {@link #release}
   * 归还的是 {@code frozen}（未付的预占）——两者若共用 {@code RELEASE}， 流水就无法区分"释放冻结"与"回冲已扣减"，按流水推导"本单还占多少冻结"会算错
   * （已退款的结算单会被算成还占着冻结）。流水是事实表，事实表的类型必须无歧义。
   */
  public QuotaTransaction rollback(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    requirePositive(amount, "回冲金额");
    if (quotaClaimed.compareTo(amount) < 0) {
      throw new DomainException("已扣减额度小于回冲金额");
    }
    BigDecimal before = quotaClaimed;
    this.quotaClaimed = quotaClaimed.subtract(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    this.capUsed = capUsed.subtract(amount).max(ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    assertConserved();
    return append(
        txnId,
        claimId,
        settlementNo,
        "ROLLBACK",
        "REFUND",
        amount,
        before,
        quotaClaimed,
        idempotencyKey);
  }

  /**
   * 累计年免赔（R-DED-03）并落流水。
   *
   * <p><b>必须落 {@code txn_type=DEDUCTIBLE} 的流水</b>：{@code deductible_used} 是账户投影，流水才是事实。 不落流水就无法按
   * {@code idempotencyKey} 判重（重复提交会把免赔消抵两次 ⇒ 系统性少赔）， 撤件/重算时也没有可据以回退的依据。金额为 0 时不落流水（免赔未消抵，无需记账）。
   *
   * @return 本次流水；{@code amount <= 0} 时返回 {@code null}
   */
  public QuotaTransaction accumulateDeductible(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    if (amount == null || amount.signum() <= 0) {
      return null;
    }
    BigDecimal before = deductibleUsed;
    this.deductibleUsed = deductibleUsed.add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    this.updatedAt = Instant.now();
    return append(
        txnId,
        claimId,
        settlementNo,
        "OCCUPY",
        "DEDUCTIBLE",
        amount,
        before,
        deductibleUsed,
        idempotencyKey);
  }

  /**
   * 累计<b>当日</b>赔付占用（Q-05 日赔付限额）。
   *
   * <p><b>它不是第四本账，而是一条事实</b>：日用量是"同一业务日已经占用了多少"的事件，落成 {@code txn_type=DAILY} + {@code biz_date}
   * 的流水行，读侧按业务日聚合即可。因此它既不改变 {@code frozen}/{@code claimed}（不参与守恒校验），也不需要"每日清零"这种批处理——清零是状态，
   * 状态就会漏跑；按日聚合是纯函数，补赔/倒签发票都能算对。
   *
   * <p><b>缺业务日即不落账</b>：没有业务日的钱算不到任何一天，硬记一笔反而会污染某天的额度。
   *
   * @param bizDate 业务日（发票日期 / 赔案受理日，按责任配置的 {@code daily_limit_basis}）
   * @return 本次流水；{@code amount <= 0} 或业务日缺失时返回 {@code null}
   */
  public QuotaTransaction accumulateDaily(
      long txnId,
      BigDecimal amount,
      java.time.LocalDate bizDate,
      Long claimId,
      String settlementNo,
      String idempotencyKey) {
    if (amount == null || amount.signum() <= 0 || bizDate == null) {
      return null;
    }
    return append(
        txnId,
        claimId,
        settlementNo,
        "OCCUPY",
        "DAILY",
        amount,
        ZERO,
        ZERO,
        idempotencyKey,
        bizDate);
  }

  /**
   * 回退<b>当日</b>赔付占用（撤销 / 拒赔 / 退款，与 {@link #accumulateDaily} 严格对称）。
   *
   * <p>不回退的后果是单向的：当日额度被一笔已经撤销的赔案永久吃掉，当天后续真实赔案被限额拦下（少赔）。
   *
   * @return 本次流水；{@code amount <= 0} 或业务日缺失时返回 {@code null}
   */
  public QuotaTransaction reduceDaily(
      long txnId,
      BigDecimal amount,
      java.time.LocalDate bizDate,
      Long claimId,
      String settlementNo,
      String idempotencyKey) {
    if (amount == null || amount.signum() <= 0 || bizDate == null) {
      return null;
    }
    return append(
        txnId,
        claimId,
        settlementNo,
        "ROLLBACK",
        "DAILY",
        amount,
        ZERO,
        ZERO,
        idempotencyKey,
        bizDate);
  }

  /**
   * 回退年免赔（撤件 / 拒赔 / 冲正：本案消抵的免赔须归还，否则后续案件会重复"消化"同一笔免赔 ⇒ 系统性少赔）。
   *
   * <p>与 {@link #accumulateDeductible} 严格对称：方向 {@code ROLLBACK}，且钳到 0（历史上限保护）。
   *
   * @return 本次流水；{@code amount <= 0} 时返回 {@code null}
   */
  public QuotaTransaction reduceDeductible(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    if (amount == null || amount.signum() <= 0) {
      return null;
    }
    BigDecimal before = deductibleUsed;
    this.deductibleUsed =
        deductibleUsed.subtract(amount).max(ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    this.updatedAt = Instant.now();
    return append(
        txnId,
        claimId,
        settlementNo,
        "ROLLBACK",
        "DEDUCTIBLE",
        amount,
        before,
        deductibleUsed,
        idempotencyKey);
  }

  /**
   * 回退<b>本结算单自有</b>的年免赔消抵。
   *
   * <p>年免赔同样是<b>共享池</b>（一个被保人一年的免赔被多案先后消抵），所以回退必须限定在
   * 本单实际消抵过的额度内：从未消抵免赔的结算单若去回退，会把<b>别的案件</b>已经消抵掉的免赔 退回来 ⇒ 客户下一案又要重新消化一笔根本没赔出去的免赔（系统性少赔）。
   *
   * <p>三态判定与 {@link #releaseOwned} 同构：{@code ownDeductible == 0} ⇒ 空操作； {@code 0 < ownDeductible <
   * amount} ⇒ 抛异常（账实不符，fail-closed）；否则正常回退。
   *
   * @param ownDeductible 本单自有免赔消抵额；{@code null} 视为 0
   * @return 本次流水；空操作时返回 {@code null}
   */
  public QuotaTransaction reduceDeductibleOwned(
      long txnId,
      BigDecimal amount,
      BigDecimal ownDeductible,
      Long claimId,
      String settlementNo,
      String idempotencyKey) {
    if (amount == null || amount.signum() <= 0) {
      return null;
    }
    BigDecimal owned = ownDeductible == null ? ZERO : ownDeductible;
    if (owned.signum() <= 0) {
      return null; // 本单从未消抵免赔：空操作，不动共享池
    }
    if (owned.compareTo(amount) < 0) {
      throw new DomainException("本单自有免赔消抵 " + owned + " 小于待回退 " + amount + "：免赔账实不符，拒绝越权回退他人免赔");
    }
    return reduceDeductible(txnId, amount, claimId, settlementNo, idempotencyKey);
  }

  /** 减人（不再参与额度累计）。 */
  public void markRemoved() {
    this.insuredState = 1;
  }

  public boolean isRemoved() {
    return insuredState != null && insuredState == 1;
  }

  List<QuotaTransaction> transactions() {
    return Collections.unmodifiableList(transactions);
  }

  private QuotaTransaction append(
      long txnId,
      Long claimId,
      String settlementNo,
      String direction,
      String txnType,
      BigDecimal amount,
      BigDecimal before,
      BigDecimal after,
      String idempotencyKey) {
    return append(
        txnId,
        claimId,
        settlementNo,
        direction,
        txnType,
        amount,
        before,
        after,
        idempotencyKey,
        null);
  }

  private QuotaTransaction append(
      long txnId,
      Long claimId,
      String settlementNo,
      String direction,
      String txnType,
      BigDecimal amount,
      BigDecimal before,
      BigDecimal after,
      String idempotencyKey,
      java.time.LocalDate bizDate) {
    Instant now = Instant.now();
    this.updatedAt = now;
    QuotaTransaction txn =
        QuotaTransaction.of(
            txnId,
            getTenantId(),
            getId(),
            claimId,
            settlementNo,
            direction,
            txnType,
            amount,
            before,
            after,
            idempotencyKey,
            now,
            bizDate);
    this.transactions.add(txn);
    return txn;
  }

  /** 守恒自校验（写路径强制，§11.7 不变量 1 ②④）。 */
  private void assertConserved() {
    BigDecimal computed = quotaInitial.subtract(quotaClaimed).subtract(quotaFrozen);
    if (quotaClaimed.signum() < 0 || quotaFrozen.signum() < 0 || computed.signum() < 0) {
      throw new DomainException(
          "额度守恒被破坏：initial="
              + quotaInitial
              + " claimed="
              + quotaClaimed
              + " frozen="
              + quotaFrozen);
    }
  }

  private static void requirePositive(BigDecimal amount, String label) {
    if (amount == null || amount.signum() <= 0) {
      throw new DomainException(label + "必须大于 0");
    }
  }
}
