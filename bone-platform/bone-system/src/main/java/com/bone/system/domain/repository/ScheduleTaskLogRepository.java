package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.schedule.ScheduleTaskLog;

/**
 * 定时任务执行记录仓储端口：写侧 + 本聚合读（ADR-0030）。
 *
 * <p>读只有一种形态——「某任务的执行历史，新的在前」，故不拆 QueryPort（同 {@link SystemLogRepository} 的判据）。
 */
public interface ScheduleTaskLogRepository extends Repository<ScheduleTaskLog, Long> {

  /**
   * 某任务的执行记录分页，按 {@code createdAt} 倒序（最新执行在前）。
   *
   * <p>租户过滤由 SDK Criteria 隐式施加（ADR-0029 失败关闭）：跨租户的任务 id 在本租户查询返回空页， 而不是泄露他租户的执行历史。
   */
  default PageResult<ScheduleTaskLog> pageByTask(Long taskId, int page, int size) {
    return QueryBuilder.from(ScheduleTaskLog.class)
        .and(ScheduleTaskLog::getTaskId)
        .eq(taskId)
        .orderByDesc(ScheduleTaskLog::getCreatedAt)
        .page(page, size);
  }
}
