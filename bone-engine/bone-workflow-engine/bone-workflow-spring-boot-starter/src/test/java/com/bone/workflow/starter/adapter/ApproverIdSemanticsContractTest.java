package com.bone.workflow.starter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinition.AssignmentRule;
import com.bone.workflow.domain.WorkflowDefinition.Node;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.ApproverResolver.ApproverContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * approver_id 语义契约（ADR-0044 决策 1）：approver_id 是「申请人指定审批人」， 仅对 FIELD / INITIATOR_SELECT
 * 策略生效；组织策略不读取它（由 {@code OrgRoutingDefinitionV3Test} 的 decoy 探针负责反证）。本类锁定的正是「指定审批人」这一侧：FIELD
 * 策略必须原样把单据里的 approver_id 当作办理人，且支持多审批人（集合值），字段为空时返回空交由引擎 fail-closed 挂起。
 */
class ApproverIdSemanticsContractTest {

  private static final Node NODE =
      new Node(
          "approve",
          WorkflowDefinition.NodeType.APPROVE,
          WorkflowDefinition.ApprovalMode.SINGLE,
          "审批");

  @Test
  @DisplayName("FIELD 策略：原样把 approver_id 当作指定审批人（单值）")
  void field_strategy_usesApproverIdAsDesignatedApprover() {
    ApproverResolver resolver = new RecordFieldApproverResolver(roleCode -> List.of(), null);

    List<String> result =
        resolver.resolve(
            new AssignmentRule("FIELD", Map.of("fieldCode", "approver_id")),
            NODE,
            new ApproverContext("expense", "r1", "u1", Map.of("approver_id", "U-designated")));

    assertEquals(List.of("U-designated"), result, "FIELD 策略必须把单据里的 approver_id 当作办理人");
  }

  @Test
  @DisplayName("FIELD 策略：approver_id 为集合值时展开为多审批人")
  void field_strategy_expandsCollectionValue() {
    ApproverResolver resolver = new RecordFieldApproverResolver(roleCode -> List.of(), null);

    List<String> result =
        resolver.resolve(
            new AssignmentRule("FIELD", Map.of("fieldCode", "approver_id")),
            NODE,
            new ApproverContext(
                "expense", "r1", "u1", Map.of("approver_id", List.of("U-a", "U-b"))));

    assertEquals(List.of("U-a", "U-b"), result, "集合值应展开为多名办理人");
  }

  @Test
  @DisplayName("FIELD 策略：approver_id 为空时返回空（交给引擎挂起，而非放行）")
  void field_strategy_emptyValue_yieldsEmpty() {
    ApproverResolver resolver = new RecordFieldApproverResolver(roleCode -> List.of(), null);

    List<String> result =
        resolver.resolve(
            new AssignmentRule("FIELD", Map.of("fieldCode", "approver_id")),
            NODE,
            new ApproverContext("expense", "r1", "u1", Map.of()));

    assertTrue(result.isEmpty(), "字段为空必须返回空列表，让引擎 fail-closed 挂起");
  }

  @Test
  @DisplayName("INITIATOR_SELECT 策略同样读取 approver_id（与 FIELD 同源）")
  void initiatorSelect_strategy_usesApproverId() {
    ApproverResolver resolver = new RecordFieldApproverResolver(roleCode -> List.of(), null);

    List<String> result =
        resolver.resolve(
            new AssignmentRule("INITIATOR_SELECT", Map.of("fieldCode", "approver_id")),
            NODE,
            new ApproverContext("expense", "r1", "u1", Map.of("approver_id", "U-x")));

    assertEquals(List.of("U-x"), result);
  }
}
