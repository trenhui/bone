package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
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
   * 任务分页：关键词模糊（<b>名称或处理器</b>任一列命中）+ 状态精确过滤，按创建时间倒序。
   *
   * <p>跨列 OR 必须用 {@link Criteria#or(Consumer)} 条件组：子条件被括号包裹为一个原生片段， 与外层 AND 安全组合为 {@code (name LIKE
   * ? OR handler LIKE ?) AND status = ?}。 QueryBuilder 的扁平 {@code or()} 链在后续还有 {@code and()} 时会被
   * SQL 优先级退化为 {@code a OR (b AND c)}—— 停用任务仅名称命中也会漏过状态过滤（UI placeholder 承诺「任务名称 /
   * 处理器」两维搜索，这里必须两维都真生效）。
   *
   * <p>排序从「无 ORDER BY 的自然序」改为 {@code createdAt} 倒序：自然序在 OR 查询下不保证跨页稳定， 分页可能重复/丢行；与本模块其他关键词分页（{@link
   * SystemConfigRepository#pageByKeyword}）保持一致。
   *
   * @param keyword 为空或空白时不加关键词过滤；{@code status} 为空时不过滤状态
   */
  default PageResult<ScheduleTask> pageByKeywordAndStatus(
      String keyword, TaskStatus status, int page, int size) {
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    var criteria = Criteria.<ScheduleTask>create().entityClass(ScheduleTask.class);
    if (hasKeyword) {
      criteria.or(
          sub -> sub.like(ScheduleTask::getName, keyword).like(ScheduleTask::getHandler, keyword));
    }
    if (status != null) {
      criteria.eq(ScheduleTask::getStatus, status);
    }
    criteria.orderByDesc(ScheduleTask::getCreatedAt).page(page, size);
    return pageByCriteria(criteria);
  }
}
