package com.bone.workflow.starter.org;

import java.util.Optional;

/**
 * 一个账户的组织位置快照：所属部门、直属主管、部门负责人。
 *
 * <p><b>为什么三个字段都可空，且空值不是错误</b>：创始人/CEO 没有上级、新人还没分配部门、小部门可能未指定负责人——
 * 这些都是真实状态。引擎拿到空会<b>挂起</b>实例并通知管理员（fail-closed），绝不会跳过审批节点。所以取值层必须如实 返回空，不能用 "" / "0"
 * 之类的哨兵把「没有」伪装成「有」。
 *
 * @param accountId 被查询的账户
 * @param deptId 所属部门；未归属时 null
 * @param lineManagerId 直属主管；无上级时 null
 * @param deptHeadId 所属部门负责人；无部门或未指定负责人时 null
 */
public record OrgHierarchy(
    String accountId, String deptId, String lineManagerId, String deptHeadId) {

  public Optional<String> lineManager() {
    return present(lineManagerId);
  }

  public Optional<String> deptHead() {
    return present(deptHeadId);
  }

  public Optional<String> dept() {
    return present(deptId);
  }

  private static Optional<String> present(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
  }
}
