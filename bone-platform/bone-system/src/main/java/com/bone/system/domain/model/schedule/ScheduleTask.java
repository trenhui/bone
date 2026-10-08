package com.bone.system.domain.model.schedule;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 系统定时任务聚合（租户自配的自身业务定时任务）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_schedule_task")
public class ScheduleTask extends TenantAggregateRoot<Long> {

  /**
   * 逻辑删除标记。
   *
   * <p><b>为何必须显式声明</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是 <b>实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 的字段</b>，与 DDL 有无 {@code deleted} 列无关。 {@code
   * TenantAggregateRoot} 只提供 {@code tenantId}，不像 {@code AbstractEntity} 那样自带该字段； 本类若不声明，{@code
   * Repository#deleteById} 会发出 {@code DELETE FROM} —— 删除即整行物理消失、不可审计不可恢复。
   *
   * <p>本表<b>无唯一索引</b>，故恢复软删不存在「同值无法重建」冲突（见soft-delete-declaration-baseline.json 的 {@code
   * _uk_conflict} 段）。
   */
  @Deleted private Boolean deleted = false;

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @com.bone.metadata.sdk.domain.annotation.Version private Long version;

  private String name;
  private String cron;
  private String handler;
  private TaskStatus status;
  private LocalDateTime lastRunAt;
  private LocalDateTime nextRunAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /**
   * 创建定时任务。
   *
   * @param tenantId 归属租户；必须显式传入并落库，否则 SDK {@code isTenantScoped()} 为 false，{@code
   *     ScheduleTaskRepository.findEnabled()} 会扫描全表（该方法 javadoc 已声明「本租户」语义）
   */
  public static ScheduleTask create(
      Long id, Long tenantId, String name, String cron, String handler, TaskStatus status) {
    ScheduleTask task = new ScheduleTask();
    task.id = id;
    task.setTenantId(tenantId);
    task.name = name;
    task.cron = cron;
    task.handler = handler;
    task.status = status == null ? TaskStatus.DISABLED : status;
    task.createdAt = LocalDateTime.now();
    task.updatedAt = LocalDateTime.now();
    return task;
  }

  public void update(String name, String cron, String handler) {
    this.name = name;
    this.cron = cron;
    this.handler = handler;
    this.updatedAt = LocalDateTime.now();
  }

  public void enable() {
    this.status = TaskStatus.ENABLED;
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    this.status = TaskStatus.DISABLED;
    this.updatedAt = LocalDateTime.now();
  }

  public void markRun(LocalDateTime runAt, LocalDateTime nextAt) {
    this.lastRunAt = runAt;
    this.nextRunAt = nextAt;
    this.updatedAt = LocalDateTime.now();
  }
}
