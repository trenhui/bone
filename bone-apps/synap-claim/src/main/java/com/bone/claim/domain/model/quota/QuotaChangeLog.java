package com.bone.claim.domain.model.quota;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 额度保全记录（{@code t_quota_change_log}，append-only）——参考实现的"保全记录"。
 *
 * <p><b>为什么单独一张表而不是复用 {@link QuotaTransaction}</b>：后者是<b>理赔占用流水</b> （{@code direction}
 * OCCUPY/ROLLBACK、{@code txn_type} SOFT/HARD/PAID/...，守恒校验依赖它）， 而保全是<b>配置变更</b>（改 {@code
 * quota_initial}，不影响 {@code claimed}/{@code frozen}）。 两者语义不同：混在一张表里，按 {@code txn_type}
 * 统计"占用额"的读侧会把保全算进去， 守恒口径随之算歪（"账户当前值 = 全部流水汇总"这条不变量就假绿了）。故分表。
 *
 * <p><b>不变式</b>：保全记录只追加，不改不删（与 {@code t_quota_transaction} 同款纪律）。 {@code before_initial} / {@code
 * after_initial} 记的是<b>初始额度绝对值快照</b>而非增量， 这样任意两条相邻记录可以直接接续成时间线（"谁在什么时候把额度从 A 调到 B"）， 不必回放全部流水。
 */
@Getter
@Table("t_quota_change_log")
public class QuotaChangeLog extends AbstractEntity<Long> {

  /** 变更类型：开户。 */
  public static final String TYPE_INIT = "INIT";

  /** 变更类型：调额。 */
  public static final String TYPE_ADJUST = "ADJUST";

  /** 变更类型：减人。 */
  public static final String TYPE_REMOVE = "REMOVE";

  /** 变更类型：恢复（撤销减人）。 */
  public static final String TYPE_RESTORE = "RESTORE";

  @Column(name = "quota_account_id", nullable = false)
  private Long quotaAccountId;

  @Column(name = "policy_no", nullable = false)
  private String policyNo;

  @Column(name = "insured_id_hash", nullable = false)
  private String insuredIdHash;

  @Column(name = "change_type", nullable = false)
  private String changeType;

  /** 变更前初始额度（绝对值快照）。 */
  @Column(name = "before_initial", nullable = false)
  private BigDecimal beforeInitial;

  /** 变更后初始额度（绝对值快照）。 */
  @Column(name = "after_initial", nullable = false)
  private BigDecimal afterInitial;

  @Column(name = "operator_id")
  private String operatorId;

  @Column(name = "operator_name")
  private String operatorName;

  @Column(name = "remark")
  private String remark;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  /** 租户与审计时间戳（显式声明：省略列会撞 {@code NOT NULL} 无默认值的 DDL）。 */
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "created_at", nullable = false)
  private Date createdAt;

  @Column(name = "updated_at", nullable = false)
  private Date updatedAt;

  QuotaChangeLog() {}

  private QuotaChangeLog(
      Long id,
      Long tenantId,
      Long quotaAccountId,
      String policyNo,
      String insuredIdHash,
      String changeType,
      BigDecimal beforeInitial,
      BigDecimal afterInitial,
      String operatorId,
      String operatorName,
      String remark,
      Instant occurredAt) {
    setId(id);
    this.tenantId = tenantId;
    this.quotaAccountId = quotaAccountId;
    this.policyNo = policyNo;
    this.insuredIdHash = insuredIdHash;
    this.changeType = changeType;
    this.beforeInitial = beforeInitial;
    this.afterInitial = afterInitial;
    this.operatorId = operatorId == null ? "" : operatorId;
    this.operatorName = operatorName == null ? "" : operatorName;
    this.remark = remark == null ? "" : remark;
    this.occurredAt = occurredAt;
    this.createdAt = Date.from(occurredAt);
    this.updatedAt = Date.from(occurredAt);
  }

  /** 保全记录工厂。 */
  public static QuotaChangeLog of(
      Long id,
      Long tenantId,
      Long quotaAccountId,
      String policyNo,
      String insuredIdHash,
      String changeType,
      BigDecimal beforeInitial,
      BigDecimal afterInitial,
      String operatorId,
      String operatorName,
      String remark,
      Instant occurredAt) {
    return new QuotaChangeLog(
        id,
        tenantId,
        quotaAccountId,
        policyNo,
        insuredIdHash,
        changeType,
        beforeInitial,
        afterInitial,
        operatorId,
        operatorName,
        remark,
        occurredAt);
  }
}
