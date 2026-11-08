package com.bone.claim.domain.model.quota;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 额度流水（`t_quota_transaction`，append-only）。
 *
 * <p>守恒不变量（§11.7）：账户当前值 = 该账户全部流水汇总——流水是**事实**，账户是**投影**， 二者不一致即 P0。故流水只追加、不更新不删除（DB 侧也仅授
 * INSERT/SELECT，§9.5 注入矩阵）。
 *
 * <p>幂等键 {@code {claimNo}:{stage}:{attempt}}（§7.5）：{@code attempt} 为占额周期序号，
 * 使"占额→释放→再占额"与二次退款能各自落账而不被唯一约束拦截。
 */
@Getter
@Table("t_quota_transaction")
public class QuotaTransaction extends AbstractEntity {

  /** 方向：OCCUPY 占用 / ROLLBACK 回冲。 */
  @Column(name = "direction")
  private String direction;

  /** 事务类型：SOFT 软占 / HARD 硬占 / PAID 已付 / RELEASE 释放。 */
  @Column(name = "txn_type")
  private String txnType;

  @Column(name = "quota_account_id")
  private Long quotaAccountId;

  @Column(name = "claim_id")
  private Long claimId;

  @Column(name = "settlement_no")
  private String settlementNo;

  @Column(name = "amount")
  private BigDecimal amount;

  @Column(name = "before_amount")
  private BigDecimal beforeAmount;

  @Column(name = "after_amount")
  private BigDecimal afterAmount;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "occurred_at")
  private Instant occurredAt;

  /**
   * 业务日（Q-05 日限额的累计键）：发票日期 / 赔案受理日，由占额调用方按责任配置的 {@code daily_limit_basis} 传入。
   *
   * <p><b>为什么不用 {@code occurred_at}</b>：那是落账时刻。理赔实务里 9 月的发票 10 月才赔，
   * 按落账日累计等于把"集中补赔"当成"同日多次就诊"，日限额会误伤；按业务日累计才是条款语义。 历史行为 {@code null}（不参与日累计）。
   */
  @Column(name = "biz_date")
  private java.time.LocalDate bizDate;

  /**
   * 租户与审计时间戳。
   *
   * <p>本类继承的是不带租户的 {@code AbstractEntity}，而 {@code t_quota_transaction.tenant_id} 是 {@code NOT
   * NULL}；审计列同理——不显式声明即被 INSERT 省略，直接撞非空约束。
   */
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "created_at", nullable = false)
  private Date createdAt;

  @Column(name = "updated_at", nullable = false)
  private Date updatedAt;

  QuotaTransaction() {}

  QuotaTransaction(
      Long id,
      Long tenantId,
      Long quotaAccountId,
      Long claimId,
      String settlementNo,
      String direction,
      String txnType,
      BigDecimal amount,
      BigDecimal beforeAmount,
      BigDecimal afterAmount,
      String idempotencyKey,
      Instant occurredAt) {
    setId(id);
    this.tenantId = tenantId;
    this.quotaAccountId = quotaAccountId;
    this.claimId = claimId;
    this.settlementNo = settlementNo;
    this.direction = direction;
    this.txnType = txnType;
    this.amount = amount;
    this.beforeAmount = beforeAmount;
    this.afterAmount = afterAmount;
    this.idempotencyKey = idempotencyKey;
    this.occurredAt = occurredAt;
    this.createdAt = Date.from(occurredAt);
    this.updatedAt = Date.from(occurredAt);
  }

  static QuotaTransaction of(
      Long id,
      Long tenantId,
      Long quotaAccountId,
      Long claimId,
      String settlementNo,
      String direction,
      String txnType,
      BigDecimal amount,
      BigDecimal beforeAmount,
      BigDecimal afterAmount,
      String idempotencyKey,
      Instant occurredAt) {
    return of(
        id,
        tenantId,
        quotaAccountId,
        claimId,
        settlementNo,
        direction,
        txnType,
        amount,
        beforeAmount,
        afterAmount,
        idempotencyKey,
        occurredAt,
        null);
  }

  /** 含业务日的流水（Q-05：只有 {@code txn_type=DAILY} 会带业务日）。 */
  static QuotaTransaction of(
      Long id,
      Long tenantId,
      Long quotaAccountId,
      Long claimId,
      String settlementNo,
      String direction,
      String txnType,
      BigDecimal amount,
      BigDecimal beforeAmount,
      BigDecimal afterAmount,
      String idempotencyKey,
      Instant occurredAt,
      java.time.LocalDate bizDate) {
    QuotaTransaction txn =
        new QuotaTransaction(
            id,
            tenantId,
            quotaAccountId,
            claimId,
            settlementNo,
            direction,
            txnType,
            amount,
            beforeAmount,
            afterAmount,
            idempotencyKey,
            occurredAt);
    txn.bizDate = bizDate;
    return txn;
  }
}
