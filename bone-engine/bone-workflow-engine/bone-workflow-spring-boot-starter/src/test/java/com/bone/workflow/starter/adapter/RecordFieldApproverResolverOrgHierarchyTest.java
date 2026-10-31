package com.bone.workflow.starter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinition.AssignmentRule;
import com.bone.workflow.domain.WorkflowDefinition.Node;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.ApproverResolver.ApproverContext;
import com.bone.workflow.starter.spi.InMemoryOrgHierarchyProvider;
import com.bone.workflow.starter.spi.OrgHierarchyProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RecordFieldApproverResolver} 组织层级策略解析测试。
 *
 * <p>覆盖：LINE_MANAGER / DEPT_HEAD / LEVEL_UP 经 {@link OrgHierarchyProvider} 解析出人； SPI 缺失或查不到人时返回空（→
 * 引擎挂起，失败关闭，不放行）。
 */
class RecordFieldApproverResolverOrgHierarchyTest {

  private static final Node NODE =
      new Node(
          "approve",
          WorkflowDefinition.NodeType.APPROVE,
          WorkflowDefinition.ApprovalMode.SINGLE,
          "审批");

  private ApproverContext ctx(String initiator) {
    return new ApproverContext("expense", "r1", initiator, Map.of());
  }

  @Test
  @DisplayName("LINE_MANAGER：提供 OrgHierarchyProvider 时解析出直属主管")
  void lineManager_resolvesWhenProviderPresent() {
    OrgHierarchyProvider org = new InMemoryOrgHierarchyProvider().withLineManager("u1", "mgr1");
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    List<String> result =
        resolver.resolve(new AssignmentRule("LINE_MANAGER", Map.of()), NODE, ctx("u1"));

    assertEquals(List.of("mgr1"), result);
  }

  @Test
  @DisplayName("DEPT_HEAD：解析出部门负责人")
  void deptHead_resolvesWhenProviderPresent() {
    OrgHierarchyProvider org = new InMemoryOrgHierarchyProvider().withDeptHead("u1", "head1");
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    List<String> result =
        resolver.resolve(new AssignmentRule("DEPT_HEAD", Map.of()), NODE, ctx("u1"));

    assertEquals(List.of("head1"), result);
  }

  @Test
  @DisplayName("LEVEL_UP：向上 N 级沿主管链上溯")
  void levelUp_resolvesNthManager() {
    // u1 → mgr1 → mgr2 → mgr3
    OrgHierarchyProvider org =
        new InMemoryOrgHierarchyProvider()
            .withLineManager("u1", "mgr1")
            .withLineManager("mgr1", "mgr2")
            .withLineManager("mgr2", "mgr3");
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    assertEquals(
        List.of("mgr2"),
        resolver.resolve(new AssignmentRule("LEVEL_UP", Map.of("level", 2)), NODE, ctx("u1")));
    assertEquals(
        List.of("mgr3"),
        resolver.resolve(new AssignmentRule("LEVEL_UP", Map.of("level", 3)), NODE, ctx("u1")));
  }

  @Test
  @DisplayName("LEVEL_UP 越界（已到顶层仍要再向上）：返回空 → 引擎挂起")
  void levelUp_overTop_returnsEmpty() {
    OrgHierarchyProvider org = new InMemoryOrgHierarchyProvider().withLineManager("u1", "mgr1");
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    List<String> result =
        resolver.resolve(new AssignmentRule("LEVEL_UP", Map.of("level", 5)), NODE, ctx("u1"));

    assertTrue(result.isEmpty(), "越界上溯必须返回空（失败关闭），不得臆造审批人");
  }

  @Test
  @DisplayName("OrgHierarchyProvider 缺失：LINE_MANAGER 返回空（→ 挂起），而非放行或抛错")
  void orgStrategy_withoutProvider_returnsEmpty() {
    ApproverResolver resolver = new RecordFieldApproverResolver(null, null);

    List<String> result =
        resolver.resolve(new AssignmentRule("LINE_MANAGER", Map.of()), NODE, ctx("u1"));

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("员工无主管（组织数据缺失）：返回空 → 引擎挂起，而非放行")
  void lineManager_noManager_returnsEmpty() {
    OrgHierarchyProvider org = new InMemoryOrgHierarchyProvider(); // 未配置 u1 的主管
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    List<String> result =
        resolver.resolve(new AssignmentRule("LINE_MANAGER", Map.of()), NODE, ctx("u1"));

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("部门负责人自己发单（DEPT_HEAD 解析到本人）：上溯到其主管，而不是挂起")
  void deptHead_selfLeader_escalatesToLineManager() {
    // 真实场景：部门负责人提交报销。DEPT_HEAD 必然解析到本人，
    // 若不处理，这个人的每一张单据都会永久挂在 dept 节点（实测租户 1001 的 24 个真实发起人里 10 个如此）。
    OrgHierarchyProvider org =
        new InMemoryOrgHierarchyProvider()
            .withDeptHead("u1", "u1") // u1 就是本部门负责人
            .withLineManager("u1", "boss"); // u1 的主管是 boss
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    List<String> result =
        resolver.resolve(new AssignmentRule("DEPT_HEAD", Map.of()), NODE, ctx("u1"));

    assertEquals(List.of("boss"), result, "必须换成上级来审，而不是返回空（挂起）或返回本人（自审批）：节点不能少，但人必须换");
  }

  @Test
  @DisplayName("部门负责人自己发单且无主管（顶层）：返回空 → 挂起，不得退回本人")
  void deptHead_selfLeaderWithoutManager_returnsEmpty() {
    // 上溯到顶仍无人的正确结局是挂起等人工指派，绝不能退回「本人审自己」。
    OrgHierarchyProvider org =
        new InMemoryOrgHierarchyProvider().withDeptHead("u1", "u1"); // 无 lineManager
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    List<String> result =
        resolver.resolve(new AssignmentRule("DEPT_HEAD", Map.of()), NODE, ctx("u1"));

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("主管自环（manager_id = 自己）：上溯无效，返回空而非死循环")
  void lineManager_selfLoop_returnsEmpty() {
    OrgHierarchyProvider org =
        new InMemoryOrgHierarchyProvider().withLineManager("u1", "u1"); // 数据脏：自己是自己的主管
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    List<String> result =
        resolver.resolve(new AssignmentRule("LINE_MANAGER", Map.of()), NODE, ctx("u1"));

    assertTrue(result.isEmpty(), "自环上溯后仍是本人，必须判为解析不出（挂起），不得成环");
  }
}
