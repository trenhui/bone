package com.bone.system.domain.model.schedule;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.PhysicalDelete;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.schedule.valueobject.TaskRunStatus;
import com.bone.system.domain.model.schedule.valueobject.TriggerType;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定时任务单次执行记录（append-only 流水，业界标配的 task log）。
 *
 * <p><b>与 {@link ScheduleTask} 的分工</b>：后者是「当前状态」（{@code lastRunAt/nextRunAt} 会被下一次执行覆盖），
 * 本类是「历史流水」——每次执行一行，失败可追溯、耗时可观测。没有本表，「任务悄悄失败了」只能去翻服务器日志。
 *
 * <p><b>为什么是快照而非引用</b>：{@code taskName/handler} 取执行那一刻的值，任务之后改名/改处理器不污染历史记录的可读性 （与 {@code
 * AlertRecord} 存 {@code ruleName} 同构）。
 *
 * <p><b>物理删除</b>：append-only 流水，保留期清理即永久移除（同 {@code SystemLog}）；没有软删语义—— 软删一张只增不改的流水表只会让每次插入多带一个恒为
 * 0 的列。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@PhysicalDelete(reason = "append-only 执行流水：按保留期清理，删除即永久移除是既定语义")
@Table("sys_schedule_task_log")
public class ScheduleTaskLog extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long taskId;
  private String taskName;
  private String handler;
  private TriggerType triggerType;
  private TaskRunStatus runStatus;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private Long durationMs;
  private String message;
  private LocalDateTime createdAt;

  /**
   * 记录一次执行。
   *
   * @param tenantId 归属租户；必须显式传入并落库，否则 SDK {@code isTenantScoped()} 为 false，{@code
   *     ScheduleTaskLogRepository#pageByTask} 会扫描全表（与 {@link ScheduleTask} 同一约束）
   */
  public static ScheduleTaskLog record(
      Long id,
      Long tenantId,
      Long taskId,
      String taskName,
      String handler,
      TriggerType triggerType,
      TaskRunStatus runStatus,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      String message) {
    ScheduleTaskLog log = new ScheduleTaskLog();
    log.id = id;
    log.setTenantId(tenantId);
    log.taskId = taskId;
    log.taskName = taskName;
    log.handler = handler;
    log.triggerType = triggerType;
    log.runStatus = runStatus;
    log.startedAt = startedAt;
    log.finishedAt = finishedAt;
    log.durationMs =
        startedAt != null && finishedAt != null
            ? Duration.between(startedAt, finishedAt).toMillis()
            : null;
    log.message = message;
    log.createdAt = LocalDateTime.now();
    return log;
  }
}
