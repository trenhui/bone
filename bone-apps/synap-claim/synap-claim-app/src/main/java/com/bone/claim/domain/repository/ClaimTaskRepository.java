package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.task.ClaimTask;
import com.bone.claim.domain.model.task.ClaimTaskStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.Instant;
import java.util.List;

/**
 * 案件任务仓储（{@code t_claim_task}）。
 *
 * <p><b>「我的待办」的列表与 total 必须同源</b>：{@link #findMyTasks} 与 {@link #countMyTasks} 共用 {@link
 * #myTaskCriteria}，避免「列表按 A 条件过滤、计数按 B 条件过滤」导致「页面 20 条、total 137」这类经典假分页 （前端按 total 算页数，翻到第 5
 * 页却是空的）。判据有两道：单测钉「造 137 条待办时 total=137、逐页之和=137、 ID 去重=137」；架构门禁钉「count 的过滤实参必须与 find
 * 去掉分页参数后完全一致」——两处口径一旦漂移即判红。
 *
 * <p><b>排序固定 priority 升序 + created_at 升序</b>：待办列表的顺序就是处理顺序。 用 created_at 倒序会让「最早进来的案件被压到最后一页」——SLA
 * 直接爆掉。 分页必须带确定性排序，否则翻页会重复或漏行。
 *
 * <p><b>Criteria 只在这里构造，不上浮到 application</b>：DDD E-4.2 要求读侧 DSL 留在仓储/查询层后面， 应用层只拿结果。把 DSL
 * 放应用层会让「换个持久化实现」变成改业务代码。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimTaskRepository extends Repository<ClaimTask, Long> {

  /**
   * 「我的待办」过滤条件（列表与计数共用，唯一口径）。
   *
   * <p>{@code status} 为空时取<b>全部生效态</b>（PENDING_CLAIM/ASSIGNED/PROCESSING）—— 终态任务不在待办里，但也不能靠「status
   * != DONE」这种取反写法（新增终态会被静默漏掉）， 故显式枚举生效态集合，与生成列 {@code active_key} 的口径同源。
   */
  private static Criteria<ClaimTask> myTaskCriteria(
      Long tenantId, String assigneeId, String status) {
    Criteria<ClaimTask> criteria =
        Criteria.<ClaimTask>create().eq(ClaimTask::getTenantId, tenantId);
    if (assigneeId != null && !assigneeId.isBlank()) {
      criteria.eq(ClaimTask::getAssigneeId, assigneeId);
    }
    if (status != null && !status.isBlank()) {
      criteria.eq(ClaimTask::getStatus, status);
    } else {
      criteria.in(ClaimTask::getStatus, ClaimTaskStatus.activeNames().toArray());
    }
    return criteria;
  }

  /**
   * 我的待办（分页）。
   *
   * <p>排序必须是全序：priority 与 created_at 都会并列，而 {@code LIMIT/OFFSET} 在并列键上不保证跨查询稳定 —— 实测 137
   * 条并列数据翻页出现重复行、去重后只剩 135。故补主键作最终 tiebreaker，让「逐页之和 = total = ID 去重数」成立。
   */
  default List<ClaimTask> findMyTasks(
      Long tenantId, String assigneeId, String status, int page, int size) {
    Criteria<ClaimTask> criteria = myTaskCriteria(tenantId, assigneeId, status);
    criteria
        .orderByAsc(ClaimTask::getPriority)
        .orderByAsc(ClaimTask::getCreatedAt)
        .orderByAsc(ClaimTask::getId);
    criteria.page(page, size);
    return findByCriteria(criteria);
  }

  /** 我的待办总数（与 {@link #findMyTasks} 同源：同一 {@link #myTaskCriteria}）。 */
  default long countMyTasks(Long tenantId, String assigneeId, String status) {
    return countByCriteria(myTaskCriteria(tenantId, assigneeId, status));
  }

  /**
   * 某复核人当日完成量（审核疲劳闸门唯一事实源）。
   *
   * <p>只认库内 {@code finished_at} 事实：显式带租户、assignee、{@code DONE} 与 UTC 当日零点下界；
   * <b>不接受客户端声明的完成数</b>，也不新增 SQL 模板 / 迁移——列已存在。入参不全时返回 0， 调用方仍需通过形态判定拒绝空
   * assignee，绝不把「查不到」当成「可用」的事实。
   */
  default long countFinishedTodayByAssignee(Long tenantId, String assigneeId, Instant dayStart) {
    if (tenantId == null || assigneeId == null || assigneeId.isBlank() || dayStart == null) {
      return 0L;
    }
    return countByCriteria(
        Criteria.<ClaimTask>create()
            .eq(ClaimTask::getTenantId, tenantId)
            .eq(ClaimTask::getAssigneeId, assigneeId)
            .eq(ClaimTask::getStatus, ClaimTaskStatus.DONE.name())
            .gte(ClaimTask::getFinishedAt, dayStart));
  }

  /**
   * 同案同类型的生效任务（与唯一键 {@code uk_tenant_active} 同义）。
   *
   * <p>为什么用 SQL 模板而不是 Criteria：{@code active_key} 是生成列、<b>不在实体里</b>（SDK 按实体元数据 生成列清单），Criteria
   * 无法引用它；模板直接按状态集合判生效，语义与生成列保持一字不差。
   */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimTask findActiveByClaimAndType(
      @Param("claimId") long claimId, @Param("taskType") String taskType);

  /** 某案的全部任务（含终态，按创建时间正序；案件 360 视图用）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<ClaimTask> findByClaimId(@Param("claimId") long claimId);

  /**
   * 全租户扫描「已指派但始终未领取且已过时限」的任务（批 4 超时回池，定时 Job 专用）。
   *
   * <p>只扫 {@code ASSIGNED + claimed_at IS NULL + due_at < now}：正在办（PROCESSING）的不动，否则会把在办任务抢回池。 受
   * {@code all_tenants_scan_only_by_schedule} 与 {@code
   * schedule_only_calls_all_tenants_repository_methods} 双重约束， <b>仅 adapter.schedule 可调</b>；结果行的
   * tenantId 由调用方显式 {@code runAs} 后再写。
   */
  @TenantScope(TenantScopeMode.ALL)
  List<ClaimTask> findExpiredAssignedAllTenants(
      @Param("before") java.sql.Timestamp before, @Param("limit") int limit);

  /**
   * 部门池待领任务（无归属人 + 指定部门），按优先级与创建时间取前 N 条。
   *
   * <p>同样以 {@code id} 收尾排序：一次取前 N 条虽不翻页，但并列键会让「同一查询两次执行返回不同集合」， 领取侧按结果批量处理时会漏掉本该在前 N 里的行。
   */
  default List<ClaimTask> findPoolTasks(String deptId, int limit) {
    Criteria<ClaimTask> criteria =
        Criteria.<ClaimTask>create()
            .eq(ClaimTask::getAssignDeptId, deptId)
            .eq(ClaimTask::getStatus, ClaimTaskStatus.PENDING_CLAIM.name())
            .orderByAsc(ClaimTask::getPriority)
            .orderByAsc(ClaimTask::getCreatedAt)
            .orderByAsc(ClaimTask::getId);
    criteria.page(1, limit);
    return findByCriteria(criteria);
  }
}
