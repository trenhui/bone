package com.bone.workflow.starter.adapter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * {@link RecordFieldApproverResolver#describeFailure} 诊断文案测试。
 *
 * <p><b>为什么值得单独测</b>：这段文案是「单据卡住时运维唯一能看到的东西」。它错了不会让任何功能失败——只会让人<b>改错地方</b>： 把「IAM
 * 连不上」当「你没配主管」去补数据，或者相反。这类缺陷没有任何编译期或功能测试能抓到，只能靠断言文案里必须出现的关键信息。
 *
 * <p>因此每条断言都刻意检查三件事：<b>策略名</b>（哪个规则落空）、<b>发起人</b>（谁的单子）、<b>缺的是哪一列</b>（该去改什么）。
 */
class RecordFieldApproverResolverDiagnosticsTest {

  private static final Node NODE =
      new Node(
          "mgr",
          WorkflowDefinition.NodeType.APPROVE,
          WorkflowDefinition.ApprovalMode.SINGLE,
          "直属主管审批");

  private static ApproverContext ctx(String initiator) {
    return new ApproverContext("expense", "r1", initiator, Map.of());
  }

  @Test
  @DisplayName("LINE_MANAGER 未接入组织目录：文案点名缺的是 OrgHierarchyProvider")
  void lineManager_withoutProvider_saysSpiMissing() {
    ApproverResolver resolver = new RecordFieldApproverResolver(null, null);

    String why =
        resolver.describeFailure(new AssignmentRule("LINE_MANAGER", Map.of()), NODE, ctx("9105"));

    assertAll(
        () -> assertTrue(why.contains("LINE_MANAGER"), "必须点名策略，否则不知道是哪条规则落空：" + why),
        () -> assertTrue(why.contains("9105"), "必须带上发起人，否则不知道是谁的单子卡住了：" + why),
        () -> assertTrue(why.contains("未装配") || why.contains("未接入"), "必须说明是宿主没接 SPI：" + why),
        () ->
            assertFalse(
                why.contains("manager_id"), "没接 SPI 时不该让人去改 manager_id——那会把运维引向一条本来就对的数据：" + why));
  }

  @Test
  @DisplayName("LINE_MANAGER 已接入但查不到：点出 manager_id，且同时提示「也可能目录不可达」")
  void lineManager_withProvider_saysColumnAndUnreachable() {
    OrgHierarchyProvider org = new InMemoryOrgHierarchyProvider(); // 未配置 9105 的主管
    ApproverResolver resolver = new RecordFieldApproverResolver(null, org);

    String why =
        resolver.describeFailure(new AssignmentRule("LINE_MANAGER", Map.of()), NODE, ctx("9105"));

    assertAll(
        () -> assertTrue(why.contains("9105"), why),
        () -> assertTrue(why.contains("manager_id"), "缺的是这一列，要指名道姓：" + why),
        () -> assertTrue(why.contains("也可能"), "必须保留「也可能是目录不可达」——解析层无法区分两者，断言成一种会误导排查方向：" + why));
  }

  @Test
  @DisplayName("DEPT_HEAD：点出 leader_id（而不是 manager_id）")
  void deptHead_pointsAtLeaderColumn() {
    ApproverResolver resolver =
        new RecordFieldApproverResolver(null, new InMemoryOrgHierarchyProvider());

    String why =
        resolver.describeFailure(new AssignmentRule("DEPT_HEAD", Map.of()), NODE, ctx("9105"));

    assertAll(
        () -> assertTrue(why.contains("DEPT_HEAD"), why),
        () -> assertTrue(why.contains("leader_id"), why),
        () ->
            assertFalse(
                why.contains("manager_id"),
                "部门负责人取的是 iam_dept.leader_id，写成 manager_id 会让人去改账户表：" + why));
  }

  @Test
  @DisplayName("LEVEL_UP：文案里带上级数（不同 level 的修法不同）")
  void levelUp_includesLevelNumber() {
    ApproverResolver resolver =
        new RecordFieldApproverResolver(null, new InMemoryOrgHierarchyProvider());

    String why =
        resolver.describeFailure(
            new AssignmentRule("LEVEL_UP", Map.of("level", 3)), NODE, ctx("9105"));

    assertAll(
        () -> assertTrue(why.contains("向上 3 级"), "必须带上具体的层级数：" + why),
        () -> assertTrue(why.contains("max-levels"), "越界最常见的原因是超出配置上限，要给出这个线索：" + why));
  }

  @Test
  @DisplayName("ROLE 未配置 roleCode：指出是定义侧参数缺失，而非组织数据")
  void role_withoutCode_saysParamMissing() {
    ApproverResolver resolver = new RecordFieldApproverResolver(null, null);

    String why = resolver.describeFailure(new AssignmentRule("ROLE", Map.of()), NODE, ctx("u1"));

    assertTrue(why.contains("roleCode"), why);
  }

  @Test
  @DisplayName("ROLE 有 code 但无成员：不是参数问题，是没人挂这个角色")
  void role_withNoMembers_saysEmpty() {
    ApproverResolver resolver = new RecordFieldApproverResolver(roleCode -> List.of(), null);

    String why =
        resolver.describeFailure(
            new AssignmentRule("ROLE", Map.of("roleCode", "finance")), NODE, ctx("u1"));

    assertAll(
        () -> assertTrue(why.contains("finance"), why),
        () -> assertTrue(why.contains("没有任何成员"), why));
  }

  @Test
  @DisplayName("FIELD 字段为空：带上字段名，因为这是申请人漏填而不是配置错")
  void field_blankValue_pointsAtField() {
    ApproverResolver resolver = new RecordFieldApproverResolver(null, null);

    String why =
        resolver.describeFailure(
            new AssignmentRule("FIELD", Map.of("fieldCode", "approver_id")), NODE, ctx("u1"));

    assertAll(
        () -> assertTrue(why.contains("approver_id"), why),
        () -> assertTrue(why.contains("未填写"), "空值要归因为漏填，不要让人去查组织目录：" + why));
  }

  @Test
  @DisplayName("未知策略：明确说「不是已知策略」，避免被当成组织数据问题")
  void unknownStrategy_saysUnknown() {
    ApproverResolver resolver = new RecordFieldApproverResolver(null, null);

    String why =
        resolver.describeFailure(new AssignmentRule("BOSS_OF_BOSS", Map.of()), NODE, ctx("u1"));

    assertTrue(why.contains("不是引擎已知的指派策略"), why);
  }

  @Test
  @DisplayName("默认实现不提供诊断时返回 null（引擎据此退化，不抛异常）")
  void defaultImplementation_returnsNull() {
    ApproverResolver bare = (rule, node, context) -> List.of();

    assertNull(
        bare.describeFailure(new AssignmentRule("LINE_MANAGER", Map.of()), NODE, ctx("u1")),
        "诊断是可选能力：宿主用 lambda 实现时不该被迫提供，也不该因此抛错");
  }
}
