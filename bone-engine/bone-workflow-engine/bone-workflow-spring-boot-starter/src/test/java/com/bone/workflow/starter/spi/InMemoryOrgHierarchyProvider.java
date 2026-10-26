package com.bone.workflow.starter.spi;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 内存版组织层级提供者（测试夹具 / 演示用）。
 *
 * <p>用「账户 → 直属主管」映射模拟组织目录，向上 N 级即沿映射链上溯。用于让 {@code LINE_MANAGER} / {@code DEPT_HEAD} / {@code
 * LEVEL_UP} 在测试中真实解析出人，从而端到端验证「报销按直属主管审批」「采购部门负责人 + 向上 N 级副总」等企业场景。
 *
 * <p><b>生产不可用</b>：真实部署应由 IAM HTTP 网关适配器实现 {@link OrgHierarchyProvider}，而非内存映射。
 */
public class InMemoryOrgHierarchyProvider implements OrgHierarchyProvider {

  /** 账户 → 直属主管。 */
  private final Map<String, String> lineManager = new HashMap<>();

  /** 账户 → 部门负责人。 */
  private final Map<String, String> deptHead = new HashMap<>();

  public InMemoryOrgHierarchyProvider withLineManager(String account, String manager) {
    lineManager.put(account, manager);
    return this;
  }

  public InMemoryOrgHierarchyProvider withDeptHead(String account, String head) {
    deptHead.put(account, head);
    return this;
  }

  @Override
  public Optional<String> lineManagerOf(String accountId) {
    return Optional.ofNullable(lineManager.get(accountId));
  }

  @Override
  public Optional<String> deptHeadOf(String accountId) {
    return Optional.ofNullable(deptHead.get(accountId));
  }

  @Override
  public Optional<String> levelUpOf(String accountId, int levels) {
    String current = accountId;
    for (int i = 0; i < levels; i++) {
      String up = lineManager.get(current);
      if (up == null) {
        return Optional.empty(); // 已到顶层仍要再向上：越界，返回空（→ 引擎挂起）
      }
      current = up;
    }
    return Optional.of(current);
  }
}
