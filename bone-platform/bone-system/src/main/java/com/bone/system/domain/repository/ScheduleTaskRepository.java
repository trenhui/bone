package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import java.util.List;

/**
 * 定时任务仓储端口：写侧 + 本聚合读（ADR-0030）。
 *
 * <p>任务既由 HTTP 用例维护，也由调度器在启动后自行装载，两者走同一个端口，避免出现「注册中心自己去写一条 SQL」 的第二条取数通道（SQL 真源唯一，见 ADR-0030）。
 */
public interface ScheduleTaskRepository extends Repository<ScheduleTask, Long> {

  /** 本租户处于启用状态的任务，供调度器启动时装载。 */
  default List<ScheduleTask> findEnabled() {
    return findByCriteria(
        Criteria.<ScheduleTask>create()
            .entityClass(ScheduleTask.class)
            .eq(ScheduleTask::getStatus, TaskStatus.ENABLED));
  }

  /**
   * 任务分页：名称模糊 + 状态精确过滤。
   *
   * @param keyword 为空或空白时不加名称过滤；{@code status} 为空时不过滤状态
   */
  default PageResult<ScheduleTask> pageByKeywordAndStatus(
      String keyword, TaskStatus status, int pageNum, int pageSize) {
    var query = QueryBuilder.from(ScheduleTask.class);
    if (keyword != null && !keyword.isBlank()) {
      query.where(ScheduleTask::getName).contains(keyword);
    }
    if (status != null) {
      query.and(ScheduleTask::getStatus).eq(status);
    }
    return query.page(pageNum, pageSize);
  }
}
