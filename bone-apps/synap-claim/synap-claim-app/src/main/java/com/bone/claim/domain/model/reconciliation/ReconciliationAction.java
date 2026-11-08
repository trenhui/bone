package com.bone.claim.domain.model.reconciliation;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import lombok.Getter;

/**
 * 对账差异的<b>处置流水</b>（{@code t_reconciliation_action}，append-only）。
 *
 * <p><b>为什么处置不写回差异行</b>：{@code t_reconciliation_diff} 是对账结论的证据，
 * 一旦允许"更新处置字段"，就等于允许改写证据（先判红、后改动、再声称从没红过）。 处置动作全部追加到本表，"某条差异现在的处置态"＝它最新的一条处置流水；
 * 待处置队列＝没有处置流水的差异。代价是查询要 join，换来的是"每一次处置都留痕、且删不掉"。
 *
 * <p><b>为什么处置动作不配双人复核</b>：本表记录的是"判定与说明"（RESOLVED/WAIVED 之类的结论），不是钱。
 * 涉及金额的动作只有两种，都不在这里：调账走补偿流水（§6.4，已有双人复核）； 投影重算走 {@code
 * QuotaProjectionRepairService}（无目标值入参、只写派生列）。给"标记已处理"加审批， 只会让人误以为它是改账入口。
 *
 * <p><b>投影重建也落这张表</b>：它此前只写应用日志（{@code [额度投影重建]} 前缀），库内不可查—— "谁在何时重算过哪个账户、改动了哪几列、从多少到多少"只能翻日志文件。
 * 处置流水是同一类事实（对系统状态施加的一次受控动作），放同一张表，运维排障只查一处。
 */
@Getter
@Table("t_reconciliation_action")
public class ReconciliationAction extends TenantAbstractEntity<Long> {

  /** 处置目标：对账差异。 */
  public static final String TARGET_DIFF = "DIFF";

  /** 处置目标：额度账户（投影重建）。 */
  public static final String TARGET_QUOTA_ACCOUNT = "QUOTA_ACCOUNT";

  /** 动作：对差异给出处置结论。 */
  public static final String ACTION_DIFF_RESOLVE = "DIFF_RESOLVE";

  /** 动作：按流水重建额度账户投影。 */
  public static final String ACTION_PROJECTION_REBUILD = "PROJECTION_REBUILD";

  /** {@code detail} 的列宽上限（VARCHAR(512)，超长直接截断并在末尾标记）。 */
  public static final int DETAIL_MAX_LENGTH = 512;

  @Column(name = "target_type", nullable = false)
  private String targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Column(name = "action_type", nullable = false)
  private String actionType;

  /** 处置结论（仅 {@link #ACTION_DIFF_RESOLVE} 有值：{@code RESOLVED/ADJUSTED/WAIVED}）。 */
  @Column(name = "resolution")
  private String resolution;

  /** 处置说明 / 重算的逐列 before→after（自由文本，供排障时直接读，不必再拼表）。 */
  @Column(name = "detail")
  private String detail;

  @Column(name = "operator_id", nullable = false)
  private String operatorId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  ReconciliationAction() {}

  /**
   * 差异处置动作。
   *
   * @param tenantId 租户
   * @param diffId 差异 ID（{@code t_reconciliation_diff.id}）
   * @param resolution 处置结论（已经过 {@link ReconciliationDiff#resolve} 校验）
   * @param detail 处置说明（可为空；超长截断）
   * @param operatorId 操作人（取自鉴权身份，不接受请求体自报）
   * @param occurredAt 动作时刻
   */
  public static ReconciliationAction diffResolved(
      Long tenantId,
      Long diffId,
      String resolution,
      String detail,
      String operatorId,
      Instant occurredAt) {
    return create(
        tenantId,
        TARGET_DIFF,
        diffId,
        ACTION_DIFF_RESOLVE,
        resolution,
        detail,
        operatorId,
        occurredAt);
  }

  /**
   * 投影重建动作。
   *
   * @param accountId 额度账户 ID
   * @param deltas 被改写的列及前后值（{@code changed=false} 时为空，本方法只应在真的改了东西时调用）
   */
  public static ReconciliationAction projectionRebuilt(
      Long tenantId,
      Long accountId,
      List<QuotaDelta> deltas,
      String operatorId,
      Instant occurredAt) {
    StringBuilder detail = new StringBuilder();
    for (QuotaDelta delta : deltas == null ? List.<QuotaDelta>of() : deltas) {
      if (detail.length() > 0) {
        detail.append("; ");
      }
      detail
          .append(delta.field())
          .append(' ')
          .append(plain(delta.before()))
          .append("->")
          .append(plain(delta.after()));
    }
    return create(
        tenantId,
        TARGET_QUOTA_ACCOUNT,
        accountId,
        ACTION_PROJECTION_REBUILD,
        "",
        detail.toString(),
        operatorId,
        occurredAt);
  }

  /** 投影重建的列级差异（{@code application} 层的 {@code ColumnDelta} 由此落到流水文本，避免 domain 依赖 application）。 */
  public record QuotaDelta(String field, BigDecimal before, BigDecimal after) {}

  private static ReconciliationAction create(
      Long tenantId,
      String targetType,
      Long targetId,
      String actionType,
      String resolution,
      String detail,
      String operatorId,
      Instant occurredAt) {
    if (operatorId == null || operatorId.isBlank()) {
      // 无操作人的处置流水等于没有留痕：宁可写入失败，也不要落一条"某人"的空壳
      throw new IllegalArgumentException("处置流水必须记录操作人");
    }
    ReconciliationAction action = new ReconciliationAction();
    action.setTenantId(tenantId);
    action.targetType = targetType;
    action.targetId = targetId;
    action.actionType = actionType;
    action.resolution = resolution == null ? "" : resolution;
    action.detail = truncate(detail);
    action.operatorId = operatorId;
    action.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    // 审计列由 SDK 映射但不会自动填充：DDL 里 created_at 是 NOT NULL 无默认值，落库前必须显式赋值
    Date now = Date.from(action.occurredAt);
    action.setCreatedAt(now);
    action.setUpdatedAt(now);
    return action;
  }

  private static String truncate(String detail) {
    if (detail == null || detail.isBlank()) {
      return "";
    }
    if (detail.length() <= DETAIL_MAX_LENGTH) {
      return detail;
    }
    return detail.substring(0, DETAIL_MAX_LENGTH - 3) + "...";
  }

  private static String plain(BigDecimal value) {
    return value == null ? "null" : value.toPlainString();
  }
}
