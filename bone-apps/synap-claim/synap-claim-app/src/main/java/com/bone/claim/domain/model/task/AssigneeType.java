package com.bone.claim.domain.model.task;

/** 归属主体类型（规则目标与任务归属的来源）。 */
public enum AssigneeType {
  /** 具体的人（用户 ID）。 */
  USER,
  /** 部门池（部门 ID；领取时按部门展开成人）。 */
  DEPT,
  /** 角色池（角色码；领取时按角色成员展开）。 */
  ROLE
}
