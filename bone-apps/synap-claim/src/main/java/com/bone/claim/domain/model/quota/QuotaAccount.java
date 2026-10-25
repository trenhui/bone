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
   * 冻结（理算完成后占额，计入 frozen 不扣 claimed）。
   *
   * @param amount 冻结金额
   * @return 本次流水（append-only）
   */
  public QuotaTransaction freeze(
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
        "HARD",
        amount,
        before,
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

  /** 释放冻结（重算/撤销：frozen 归还）。 */
  public QuotaTransaction release(
      long txnId, BigDecimal amount, Long claimId, String settlementNo, String idempotencyKey) {
    requirePositive(amount, "释放金额");
    if (quotaFrozen.compareTo(amount) < 0) {
      throw new DomainException("冻结额度小于释放金额");
    }
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

  /** 回冲已付（退款/红冲：claimed 归还）。 */
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
        "RELEASE",
        amount,
        before,
        quotaClaimed,
        idempotencyKey);
  }

  /** 累计年免赔（R-DED-03）。 */
  public void accumulateDeductible(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      return;
    }
    this.deductibleUsed = deductibleUsed.add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
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
            Instant.now());
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
