package com.bone.claim.domain.model.sla;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * 案件 SLA 计时聚合（运营读模型，CQRS/ES 查询侧，ADR-003 / §6.6）。
 *
 * <p>由 {@code RefreshLimitHourJob} 每 20 分钟从 {@code t_claim_case_track} 物化写入（{@code source_of_truth
 * = track}），承载 {@code stage_entered_at}/{@code legal_due_at}/{@code sla_due_at}/{@code
 * remaining_work_hours}/{@code hang_accum_seconds}，供工作台排序、超时挂起（§6.5 {@code TIMEOUT}）与运营报表直接查询。
 *
 * <p><b>可变更读模型</b>（非 append-only）：同一 {@code (tenant_id, claim_id)} 唯一，{@link Version} 乐观锁防并发刷新。
 * 本类不承载业务不变量判定（那是 {@code ClaimCase} 聚合的职责），只做投影物化。
 */
@Getter
@Table("t_claim_case_sla")
public class ClaimCaseSla extends TenantAbstractEntity<Long> {

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "current_status", nullable = false)
  private String currentStatus;

  @Column(name = "current_stage", nullable = false)
  private String currentStage;

  /** 进入当前阶段的时间（阶段停留计时起点，取自 track 最后一次跃迁）。 */
  @Column(name = "stage_entered_at", nullable = false)
  private Instant stageEnteredAt;

  /** 法定时限截止（自然日，§6.6 法定节点，不可放宽）。 */
  @Column(name = "legal_due_at")
  private Instant legalDueAt;

  /** 内部作业 SLA 截止（一期近似：阶段进入时间 + 目标工时自然小时）。 */
  @Column(name = "sla_due_at")
  private Instant slaDueAt;

  /** 工作日剩余小时（limit_hour 口径，已扣周末与挂起占用，下限 0）。 */
  @Column(name = "remaining_work_hours", nullable = false)
  private BigDecimal remainingWorkHours;

  /** 累计挂起秒数（工作日口径，供报表与排序参考）。 */
  @Column(name = "hang_accum_seconds", nullable = false)
  private Long hangAccumSeconds;

  @Column(name = "last_refresh_at", nullable = false)
  private Instant lastRefreshAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  ClaimCaseSla() {}

  /** 首次物化（新快照）。 */
  public static ClaimCaseSla create(
      Long tenantId,
      Long claimId,
      String currentStatus,
      String currentStage,
      Instant stageEnteredAt,
      Instant legalDueAt,
      Instant slaDueAt,
      BigDecimal remainingWorkHours,
      long hangAccumSeconds,
      Instant now) {
    ClaimCaseSla sla = new ClaimCaseSla();
    sla.setTenantId(tenantId);
    sla.claimId = claimId;
    sla.version = 0L;
    sla.apply(
        currentStatus,
        currentStage,
        stageEnteredAt,
        legalDueAt,
        slaDueAt,
        remainingWorkHours,
        hangAccumSeconds,
        now);
    return sla;
  }

  /** 刷新既有快照（同一聚合实例再次物化，乐观锁由 SDK 处理）。 */
  public void refresh(
      String currentStatus,
      String currentStage,
      Instant stageEnteredAt,
      Instant legalDueAt,
      Instant slaDueAt,
      BigDecimal remainingWorkHours,
      long hangAccumSeconds,
      Instant now) {
    apply(
        currentStatus,
        currentStage,
        stageEnteredAt,
        legalDueAt,
        slaDueAt,
        remainingWorkHours,
        hangAccumSeconds,
        now);
  }

  private void apply(
      String currentStatus,
      String currentStage,
      Instant stageEnteredAt,
      Instant legalDueAt,
      Instant slaDueAt,
      BigDecimal remainingWorkHours,
      long hangAccumSeconds,
      Instant now) {
    this.currentStatus = currentStatus;
    this.currentStage = currentStage;
    this.stageEnteredAt = stageEnteredAt;
    this.legalDueAt = legalDueAt;
    this.slaDueAt = slaDueAt;
    this.remainingWorkHours = remainingWorkHours == null ? BigDecimal.ZERO : remainingWorkHours;
    this.hangAccumSeconds = Math.max(0L, hangAccumSeconds);
    this.lastRefreshAt = now;
  }
}
