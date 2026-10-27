package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 试运行器的判别力测试。
 *
 * <p>核心命题：<b>同源</b>——试运行必须复用运行期的条件求值器与分支选择器，因此「模拟说走 A」必须等于「实际走 A」。本类用 fail-closed
 * 的三种典型形态（字段缺省、分支重叠、无默认边）各钉一例；这三条若被改成「按 false 处理」，本类必红。
 */
class WorkflowSimulatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final WorkflowSimulator simulator = WorkflowSimulator.withDefaults();

  private static JsonNode json(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static WorkflowDefinition.Node node(
      String code, WorkflowDefinition.NodeType type, WorkflowDefinition.ApprovalMode mode) {
    return new WorkflowDefinition.Node(code, type, mode, code);
  }

  private static WorkflowDefinition.Edge edge(String from, String to, String when, String label) {
    return new WorkflowDefinition.Edge(from, to, when == null ? null : json(when), label);
  }

  /** start → amt(条件) → {check | mgr | cfo(默认)} → end */
  private static WorkflowDefinition branching(String highWhen) {
    return new WorkflowDefinition(
        "expense",
        "费用报销",
        1,
        List.of(
            node("start", WorkflowDefinition.NodeType.START, null),
            node("amt", WorkflowDefinition.NodeType.CONDITION, null),
            node(
                "check",
                WorkflowDefinition.NodeType.APPROVE,
                WorkflowDefinition.ApprovalMode.SINGLE),
            node(
                "mgr", WorkflowDefinition.NodeType.APPROVE, WorkflowDefinition.ApprovalMode.SINGLE),
            node(
                "cfo", WorkflowDefinition.NodeType.APPROVE, WorkflowDefinition.ApprovalMode.SINGLE),
            node("end", WorkflowDefinition.NodeType.END, null)),
        List.of(
            edge("start", "amt", null, null),
            edge(
                "amt",
                "check",
                "{\"fieldCode\":\"amount\",\"operator\":\"lte\",\"value\":5000}",
                "小额"),
            edge("amt", "mgr", highWhen, "大额"),
            edge("amt", "cfo", null, "默认"),
            edge("check", "end", null, null),
            edge("mgr", "end", null, null),
            edge("cfo", "end", null, null)),
        Map.of(
            "check",
                List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of("roleCode", "fin"))),
            "mgr",
                List.of(
                    new WorkflowDefinition.AssignmentRule("ROLE", Map.of("roleCode", "finmgr"))),
            "cfo",
                List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of("roleCode", "cfo")))));
  }

  @Test
  @DisplayName("小额 → 命中 check 分支，且轨迹写明另一分支不成立")
  void smallAmount_takesCheapBranch() {
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(
            branching("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
            Map.of("amount", 3000),
            ConditionContext.empty());
    assertTrue(simulation.succeeded(), simulation.error());
    assertEquals(List.of("start", "amt", "check", "end"), simulation.path());
    WorkflowSimulator.ConditionTrace trace = simulation.conditionTraces().get(0);
    assertEquals("amt", trace.nodeCode());
    assertEquals(Boolean.TRUE, trace.branches().get(0).matched());
    assertEquals(Boolean.FALSE, trace.branches().get(1).matched());
    // 默认边不参与求值，matched 为 null（不是 false）——前端据此显示「未评估」而非「不成立」
    assertTrue(trace.branches().get(2).defaulted());
    assertEquals(null, trace.branches().get(2).matched());
  }

  @Test
  @DisplayName("大额 → 命中 mgr 分支")
  void largeAmount_takesManagerBranch() {
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(
            branching("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
            Map.of("amount", 88000),
            ConditionContext.empty());
    assertTrue(simulation.succeeded(), simulation.error());
    assertEquals(List.of("start", "amt", "mgr", "end"), simulation.path());
  }

  @Test
  @DisplayName("金额缺省 → 有序比较 fail-closed，落默认边并给出提醒（fail-open 曾导致落最低审批档）")
  void missingAmount_fallsBackToDefault() {
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(
            branching("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
            Map.of(),
            ConditionContext.empty());
    assertTrue(simulation.succeeded(), simulation.error());
    assertEquals(List.of("start", "amt", "cfo", "end"), simulation.path());
    assertTrue(
        simulation.warnings().stream().anyMatch(warning -> warning.contains("无分支命中")),
        simulation.warnings().toString());
  }

  @Test
  @DisplayName("两条分支同时命中 → 运行期拒绝推进，模拟必须同样判失败（不任选一条）")
  void overlappingBranches_failAwesomely() {
    // 两个条件在 amount=88000 时同时成立：amount>1000 与 amount>5000。
    // 这种重叠是「数据相关」的，发布期的语法检查查不出来——只有拿真实数据试运行才现形，
    // 这正是试运行存在的理由。
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                node("start", WorkflowDefinition.NodeType.START, null),
                node("amt", WorkflowDefinition.NodeType.CONDITION, null),
                node(
                    "a",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE),
                node(
                    "b",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE),
                node(
                    "c",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE),
                node("end", WorkflowDefinition.NodeType.END, null)),
            List.of(
                edge("start", "amt", null, null),
                edge(
                    "amt",
                    "a",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":1000}",
                    "大于一千"),
                edge(
                    "amt",
                    "b",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}",
                    "大于五千"),
                edge("amt", "c", null, "默认"),
                edge("a", "end", null, null),
                edge("b", "end", null, null),
                edge("c", "end", null, null)),
            Map.of(
                "a", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of())),
                "b", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of())),
                "c", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of()))));
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(definition, Map.of("amount", 88000), ConditionContext.empty());
    assertFalse(simulation.succeeded());
    assertTrue(simulation.error().contains("BRANCH_AMBIGUOUS"), simulation.error());
    assertTrue(
        simulation.warnings().stream().anyMatch(warning -> warning.contains("同时命中")),
        simulation.warnings().toString());
  }

  @Test
  @DisplayName("无默认边且无分支命中 → BRANCH_NO_MATCH（拒绝静默放行）")
  void noDefaultEdge_reportsNoMatch() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                node("start", WorkflowDefinition.NodeType.START, null),
                node("amt", WorkflowDefinition.NodeType.CONDITION, null),
                node(
                    "mgr",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE),
                node("end", WorkflowDefinition.NodeType.END, null)),
            List.of(
                edge("start", "amt", null, null),
                edge(
                    "amt",
                    "mgr",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}",
                    "大额"),
                edge("mgr", "end", null, null)),
            Map.of(
                "mgr", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of()))));
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(definition, Map.of("amount", 10), ConditionContext.empty());
    assertFalse(simulation.succeeded());
    assertTrue(simulation.error().contains("BRANCH_NO_MATCH"), simulation.error());
  }

  @Test
  @DisplayName("节点断头 → 明确报错，而不是「走到一半就结束」")
  void deadEndNode_isReported() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                node("start", WorkflowDefinition.NodeType.START, null),
                node(
                    "mgr",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE)),
            List.of(edge("start", "mgr", null, null)),
            Map.of(
                "mgr", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of()))));
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(definition, Map.of(), ConditionContext.empty());
    assertFalse(simulation.succeeded());
    assertTrue(simulation.error().contains("没有后继"), simulation.error());
  }

  @Test
  @DisplayName("存在环 → 步数超限报错，不会转圈（v1 不允许回环）")
  void cycle_isBounded() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                node("start", WorkflowDefinition.NodeType.START, null),
                node(
                    "a",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE),
                node(
                    "b",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE)),
            List.of(
                edge("start", "a", null, null),
                edge("a", "b", null, null),
                edge("b", "a", null, null)),
            Map.of(
                "a", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of())),
                "b", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of()))));
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(definition, Map.of(), ConditionContext.empty());
    assertFalse(simulation.succeeded());
    assertTrue(simulation.error().contains("步数超限"), simulation.error());
  }

  @Test
  @DisplayName("条件结构非法（未知算子）→ 轨迹里带问题描述，并以 BRANCH_INVALID 失败")
  void invalidOperator_isSurfacedInTrace() {
    Map<String, Object> data = new HashMap<>();
    data.put("amount", 100);
    WorkflowDefinition definition =
        branching("{\"fieldCode\":\"amount\",\"operator\":\"approximately\",\"value\":5000}");
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(definition, data, ConditionContext.empty());
    assertFalse(simulation.succeeded());
    assertTrue(simulation.error().contains("BRANCH_INVALID"), simulation.error());
    assertEquals(
        1,
        simulation.conditionTraces().get(0).branches().get(1).problems().size(),
        "未知算子必须在分支痕迹里点名，而不是笼统报一句「条件非法」");
  }
}
