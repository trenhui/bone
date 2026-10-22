package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 流程定义发布期校验器的判别力测试。
 *
 * <p>核心命题：<b>配错的流程不许发布</b>。每种错误配置都对应一个必红用例——把任一检查删掉，本类应变红。
 */
class WorkflowDefinitionValidatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

  private static com.fasterxml.jackson.databind.JsonNode json(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static WorkflowDefinition.Node start() {
    return new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起");
  }

  private static WorkflowDefinition.Node approve(String code) {
    return new WorkflowDefinition.Node(
        code, WorkflowDefinition.NodeType.APPROVE, WorkflowDefinition.ApprovalMode.SINGLE, code);
  }

  private static WorkflowDefinition.Node end() {
    return new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束");
  }

  private static WorkflowDefinition.Edge edge(String from, String to, String when) {
    return new WorkflowDefinition.Edge(from, to, when == null ? null : json(when), from + "→" + to);
  }

  private static WorkflowDefinition.AssignmentRule rule(String strategy) {
    return new WorkflowDefinition.AssignmentRule(strategy, Map.of());
  }

  /** 一条合法的线性流程：start → approve → end。 */
  private static WorkflowDefinition linear() {
    return new WorkflowDefinition(
        "expense",
        1,
        List.of(start(), approve("mgr"), end()),
        List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
        Map.of("mgr", List.of(rule("LINE_MANAGER"))));
  }

  private static boolean hasCode(
      List<WorkflowDefinitionValidator.Violation> violations, String code) {
    return violations.stream().anyMatch(violation -> violation.code().equals(code));
  }

  @Test
  @DisplayName("合法线性流程 → 无违规")
  void validDefinition_hasNoViolation() {
    assertTrue(validator.validate(linear()).isEmpty());
    assertTrue(validator.isPublishable(linear()));
  }

  @Test
  @DisplayName("缺发起节点 → WF-002")
  void missingStart_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(approve("mgr"), end()),
            List.of(edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-002"));
  }

  @Test
  @DisplayName("缺结束节点 → WF-003")
  void missingEnd_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr")),
            List.of(edge("start", "mgr", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-003"));
  }

  @Test
  @DisplayName("结束节点有后继 → WF-004")
  void endWithOutEdge_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null), edge("end", "mgr", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-004"));
  }

  @Test
  @DisplayName("中间节点断头 → WF-005")
  void danglingNode_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-005"));
  }

  @Test
  @DisplayName("审批节点未配指派规则 → WF-007")
  void approveWithoutAssignment_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of());
    assertTrue(hasCode(validator.validate(definition), "WF-007"));
  }

  @Test
  @DisplayName("未知指派策略 → WF-013")
  void unknownAssignmentStrategy_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("WIZARD"))));
    assertTrue(hasCode(validator.validate(definition), "WF-013"));
  }

  @Test
  @DisplayName("条件节点：出边不足 / 缺默认边 / 默认边不在末位 / 条件非法 → WF-006")
  void conditionNode_problems_areRejected() {
    WorkflowDefinition.Node condition =
        new WorkflowDefinition.Node("cond", WorkflowDefinition.NodeType.CONDITION, null, "分支");

    WorkflowDefinition onlyOneEdge =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), end()),
            List.of(
                edge("start", "cond", null),
                edge(
                    "cond",
                    "mgr",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}")),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(onlyOneEdge), "WF-006"));

    WorkflowDefinition noDefault =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), approve("dir"), end()),
            List.of(
                edge("start", "cond", null),
                edge(
                    "cond", "mgr", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
                edge(
                    "cond",
                    "dir",
                    "{\"fieldCode\":\"amount\",\"operator\":\"lt\",\"value\":1000}")),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "dir", List.of(rule("DEPT_HEAD"))));
    assertTrue(hasCode(validator.validate(noDefault), "WF-006"));

    WorkflowDefinition defaultNotLast =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), approve("dir"), end()),
            List.of(
                edge("start", "cond", null),
                edge("cond", "mgr", null),
                edge(
                    "cond",
                    "dir",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}")),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "dir", List.of(rule("DEPT_HEAD"))));
    assertTrue(hasCode(validator.validate(defaultNotLast), "WF-006"));

    WorkflowDefinition badCondition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), end()),
            List.of(
                edge("start", "cond", null),
                edge("cond", "mgr", "{\"fieldCode\":\"amount\",\"operator\":\"hack\",\"value\":1}"),
                edge("cond", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(badCondition), "WF-006"));
  }

  @Test
  @DisplayName("边指向不存在的节点 → WF-008")
  void edgeToUnknownNode_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "ghost", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-008"));
  }

  @Test
  @DisplayName("孤岛节点（从发起节点不可达）→ WF-009")
  void orphanNode_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), approve("orphan"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "orphan", List.of(rule("ROLE"))));
    assertTrue(hasCode(validator.validate(definition), "WF-009"));
  }

  @Test
  @DisplayName("流程成环 → WF-010（v1 不支持回环审批）")
  void cyclicFlow_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("a"), approve("b"), end()),
            List.of(
                edge("start", "a", null),
                edge("a", "b", null),
                edge("b", "a", null),
                edge("b", "end", null)),
            Map.of("a", List.of(rule("ROLE")), "b", List.of(rule("ROLE"))));
    assertTrue(hasCode(validator.validate(definition), "WF-010"));
  }

  @Test
  @DisplayName("非审批节点带完成判定方式 → WF-012")
  void nonApproveWithMode_isRejected() {
    WorkflowDefinition.Node badStart =
        new WorkflowDefinition.Node(
            "start", WorkflowDefinition.NodeType.START, WorkflowDefinition.ApprovalMode.ALL, "发起");
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(badStart, approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-012"));
  }
}
