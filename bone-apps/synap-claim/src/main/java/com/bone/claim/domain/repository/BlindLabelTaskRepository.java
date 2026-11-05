package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 独立盲标任务仓储。自定义 SQL 按文件名约定绑定 {@code resources/sql/…/BlindLabelTaskRepository/<方法名>.sql}。
 *
 * <p>租户口径 {@link TenantScopeMode#AUTO}：盲标载荷含案件号与票据材料引用，跨租户可见即越权读取。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface BlindLabelTaskRepository extends Repository<BlindLabelTask, Long> {

  /**
   * 按案件查「未解盲」任务（PENDING/LABELED）。
   *
   * <p>用于派发幂等：同一案件同时至多一个未解盲任务——两个标注人并行盲标同一案，结论对不上时无法归因。 解盲后的历史任务不挡新派发（同一案可隔期重标，例如规则版本升级后复测）。
   */
  BlindLabelTask findActiveByClaimId(@Param("claimId") long claimId);

  /** 按标注人查最近任务（工作台列表；上界写死在 SQL LIMIT，不是全量导出）。 */
  List<BlindLabelTask> findRecentByAssignee(@Param("assigneeId") String assigneeId);
}
