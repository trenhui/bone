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

  /**
   * 全部租户处于启用状态的任务，供调度注册中心启动时装载（跨租户）。
   *
   * <p><b>为何是跨租户扫描</b>：调度注册中心是<b>进程级</b>设施——一个实例要跑所有租户的定时任务， 否则除装载时的「当前租户」外其余租户的任务永不注册。与 {@code
   * AlertRuleRepository#findAllEnabledAllTenants}（告警评估器）同构。
   *
   * <p>显式使用 {@link Criteria#disableTenantFilter()} 逃生舱（等价于 {@code @Sql} 通道的
   * {@code @TenantScope(ALL)}）： 调用方是平台级基础设施装载而非用户请求，不存在越权风险；每条记录自带 {@code
   * tenantId}，执行与回写时以记录自身租户进入上下文。
   *
   * <p>软删过滤不受该逃生舱影响（租户与软删两条谓词彼此独立），已软删任务不会被装载。
   *
   * <p><b>命名遵循 E-4.4 / E-13.3</b>：跨租户入口必须以 {@code AllTenants} 结尾，让调用点就能看出作用域，
   * 而不是靠读方法体才发现（ArchitectureTest {@code all_tenant_entry_points_must_be_named_all_tenants}）。
   */
  default List<ScheduleTask> findEnabledAllTenants() {
    return findByCriteria(
        Criteria.<ScheduleTask>create()
            .entityClass(ScheduleTask.class)
            .disableTenantFilter() // 进程级调度注册中心：跨租户装载，见方法 javadoc
            .eq(ScheduleTask::getStatus, TaskStatus.ENABLED));
  }

  /**
   * 本租户处于启用状态的任务。
   *
   * <p>供请求线程内按当前租户读取。<b>启动装载不要用它</b>——调度线程无租户上下文，会按 ADR-0029 失败关闭抛 {@code
   * MissingTenantContextException}；启动装载走 {@link #findEnabledAllTenants()}。
   */
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
      String keyword, TaskStatus status, int page, int size) {
    var query = QueryBuilder.from(ScheduleTask.class);
    if (keyword != null && !keyword.isBlank()) {
      query.where(ScheduleTask::getName).contains(keyword);
    }
    if (status != null) {
      query.and(ScheduleTask::getStatus).eq(status);
    }
    return query.page(page, size);
  }
}
