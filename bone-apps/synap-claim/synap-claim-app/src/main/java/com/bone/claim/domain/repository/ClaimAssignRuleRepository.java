package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.task.ClaimAssignRule;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 分配规则仓储（{@code t_claim_assign_rule}）。
 *
 * <p>只提供「按任务类型取候选」一个入口：求解器负责匹配与排序，仓储不重复业务判断 （规则匹配涉及金额段边界与通配语义，写在 SQL 里就会出现「Java 判一遍、SQL 判一遍」的两套口径）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimAssignRuleRepository extends Repository<ClaimAssignRule, Long> {

  /**
   * 取某任务类型的候选规则（含 {@code *} 通配 + 仅启用）。
   *
   * <p>排序在这里也给上（priority, id）：数据库序只是<b>稳定输入</b>，真正的求解顺序仍由 {@code TaskAssigner} 固定，二者一致是为了让「同一份数据在
   * DB 与求解器里看到同样的顺序」便于审计比对。
   */
  default List<ClaimAssignRule> findCandidateRules(String taskType) {
    Criteria<ClaimAssignRule> criteria =
        Criteria.<ClaimAssignRule>create()
            .eq(ClaimAssignRule::getEnabled, true)
            .in(ClaimAssignRule::getTaskType, taskType, ClaimAssignRule.ANY_TASK_TYPE);
    criteria.orderByAsc(ClaimAssignRule::getPriority).orderByAsc(ClaimAssignRule::getId);
    return findByCriteria(criteria);
  }
}
