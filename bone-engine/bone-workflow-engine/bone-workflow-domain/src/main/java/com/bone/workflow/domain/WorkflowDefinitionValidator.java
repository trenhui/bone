package com.bone.workflow.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程定义发布期校验器：把「配错的流程」拦在发布前，不让它跑到生产单据上。
 *
 * <p><b>为什么必须有这一层</b>：流程定义是<b>业务人员可编辑</b>的元数据，配置错误的代价远高于代码 bug——一个算子
 * 拼错、一条边漏配、一个节点忘了配指派规则，都可能让单据<b>无人审批</b>（卡死）或<b>走错审批人</b>（越权放行）。
 * 故发布动作必须先过完整性校验，fail-closed：<b>有违规就不许发布</b>。
 *
 * <p>纯逻辑、无 IO：定义以 {@link WorkflowDefinition} 入参，违规以 {@link Violation} 列表出参，便于发布 API
 * 直接把清单透给建模界面（指出「哪个节点缺什么」），也便于单测。
 */
public final class WorkflowDefinitionValidator {

  /** 一条违规。 */
  public record Violation(String code, String message) {}

  /**
   * 校验流程定义。
   *
   * @param definition 待发布的定义
   * @return 违规清单（空 = 可发布）
   */
  public List<Violation> validate(WorkflowDefinition definition) {
    List<Violation> violations = new ArrayList<>();
    if (definition == null) {
      violations.add(new Violation("WF-000", "流程定义为空"));
      return violations;
    }
    List<WorkflowDefinition.Node> nodes = definition.nodes();
    List<WorkflowDefinition.Edge> edges = definition.edges();

    // —— 节点基本形态 ——
    Set<String> nodeCodes = new LinkedHashSet<>();
    for (WorkflowDefinition.Node node : nodes) {
      if (node.code() == null || node.code().isBlank()) {
        violations.add(new Violation("WF-001", "存在缺编码的节点"));
        continue;
      }
      if (!nodeCodes.add(node.code())) {
        violations.add(new Violation("WF-001", "节点编码重复：" + node.code()));
      }
      if (node.type() == null) {
        violations.add(new Violation("WF-001", "节点 " + node.code() + " 缺类型"));
        continue;
      }
      if (node.type() == WorkflowDefinition.NodeType.APPROVE && node.mode() == null) {
        violations.add(new Violation("WF-012", "审批节点 " + node.code() + " 缺完成判定方式（单签/会签/或签）"));
      }
      if (node.type() != WorkflowDefinition.NodeType.APPROVE && node.mode() != null) {
        violations.add(new Violation("WF-012", "非审批节点 " + node.code() + " 不应设置完成判定方式"));
      }
    }

    // —— 边形态：引用的节点必须存在 ——
    List<WorkflowDefinition.Edge> validEdges = new ArrayList<>();
    for (WorkflowDefinition.Edge edge : edges) {
      if (!nodeCodes.contains(edge.from()) || !nodeCodes.contains(edge.to())) {
        violations.add(new Violation("WF-008", "边引用了不存在的节点：" + edge.from() + " → " + edge.to()));
        continue;
      }
      validEdges.add(edge);
    }

    // —— 起点 / 终点 ——
    List<WorkflowDefinition.Node> starts =
        nodes.stream().filter(node -> node.type() == WorkflowDefinition.NodeType.START).toList();
    if (starts.isEmpty()) {
      violations.add(new Violation("WF-002", "缺发起节点（START）"));
    } else if (starts.size() > 1) {
      violations.add(new Violation("WF-002", "发起节点（START）必须唯一，实有 " + starts.size() + " 个"));
    }
    boolean hasEnd =
        nodes.stream().anyMatch(node -> node.type() == WorkflowDefinition.NodeType.END);
    if (!hasEnd) {
      violations.add(new Violation("WF-003", "缺结束节点（END）"));
    }

    // —— 出边完整性 + 节点类型专项 ——
    for (WorkflowDefinition.Node node : nodes) {
      if (node.code() == null) {
        continue;
      }
      List<WorkflowDefinition.Edge> outs =
          validEdges.stream().filter(edge -> edge.from().equals(node.code())).toList();
      if (node.type() == WorkflowDefinition.NodeType.END) {
        if (!outs.isEmpty()) {
          violations.add(new Violation("WF-004", "结束节点 " + node.code() + " 不应有后继"));
        }
        continue;
      }
      if (outs.isEmpty()) {
        violations.add(new Violation("WF-005", "节点 " + node.code() + " 没有后继（流程在此断头）"));
      }
      if (node.type() == WorkflowDefinition.NodeType.CONDITION) {
        lintConditionNode(node, outs, violations);
      }
      if (node.type() == WorkflowDefinition.NodeType.APPROVE) {
        lintApprovalNode(node, definition.assignments().get(node.code()), violations);
      }
    }

    // —— 同一对节点的重复边（歧义）——
    Set<String> edgeKeys = new HashSet<>();
    for (WorkflowDefinition.Edge edge : validEdges) {
      String key = edge.from() + "->" + edge.to();
      if (!edgeKeys.add(key)) {
        violations.add(new Violation("WF-011", "存在重复边：" + key));
      }
    }

    // —— 可达性 + 环检测（均以唯一 START 为前提）——
    if (starts.size() == 1) {
      String startCode = starts.get(0).code();
      Set<String> reachable = reachableFrom(startCode, validEdges);
      for (WorkflowDefinition.Node node : nodes) {
        if (node.code() != null && !reachable.contains(node.code())) {
          violations.add(new Violation("WF-009", "节点 " + node.code() + " 从发起节点不可达（孤岛节点）"));
        }
      }
      String cycle = findCycle(startCode, validEdges);
      if (cycle != null) {
        violations.add(new Violation("WF-010", "流程存在环（v1 不支持回环审批）：" + cycle));
      }
    }

    return violations;
  }

  /** 条件节点：出口数量、默认边唯一且在末位、非默认边条件必须合法。 */
  private void lintConditionNode(
      WorkflowDefinition.Node node,
      List<WorkflowDefinition.Edge> outs,
      List<Violation> violations) {
    if (outs.size() < 2) {
      violations.add(new Violation("WF-006", "条件节点 " + node.code() + " 至少需要 2 条出边（分支 + 默认）"));
    }
    List<WorkflowDefinition.Edge> defaults =
        outs.stream().filter(WorkflowDefinition.Edge::isDefault).toList();
    if (defaults.isEmpty()) {
      violations.add(new Violation("WF-006", "条件节点 " + node.code() + " 缺默认边（else）"));
    } else if (defaults.size() > 1) {
      violations.add(new Violation("WF-006", "条件节点 " + node.code() + " 存在多条默认边（else）"));
    } else if (!defaults.get(0).equals(outs.get(outs.size() - 1))) {
      violations.add(new Violation("WF-006", "条件节点 " + node.code() + " 的默认边（else）必须放在末位"));
    }
    for (WorkflowDefinition.Edge edge : outs) {
      if (edge.isDefault()) {
        continue;
      }
      for (String problem : WorkflowBranchEvaluator.lintCondition(edge.when(), "条件分支")) {
        violations.add(
            new Violation("WF-006", "条件节点 " + node.code() + " 的出边 " + edge.to() + "：" + problem));
      }
    }
  }

  /** 审批节点：必须配指派规则，且策略须在白名单内。 */
  private void lintApprovalNode(
      WorkflowDefinition.Node node,
      List<WorkflowDefinition.AssignmentRule> rules,
      List<Violation> violations) {
    if (rules == null || rules.isEmpty()) {
      violations.add(new Violation("WF-007", "审批节点 " + node.code() + " 未配置审批人指派规则"));
      return;
    }
    for (WorkflowDefinition.AssignmentRule rule : rules) {
      if (rule.strategy() == null
          || !WorkflowDefinition.SUPPORTED_STRATEGIES.contains(rule.strategy())) {
        violations.add(
            new Violation(
                "WF-013",
                "审批节点 "
                    + node.code()
                    + " 存在未知指派策略："
                    + rule.strategy()
                    + "（支持 "
                    + WorkflowDefinition.SUPPORTED_STRATEGIES
                    + "）"));
      }
    }
  }

  /** 从起点出发的可达节点集。 */
  private Set<String> reachableFrom(String startCode, List<WorkflowDefinition.Edge> edges) {
    Set<String> visited = new LinkedHashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    visited.add(startCode);
    queue.add(startCode);
    while (!queue.isEmpty()) {
      String current = queue.poll();
      for (WorkflowDefinition.Edge edge : edges) {
        if (edge.from().equals(current) && visited.add(edge.to())) {
          queue.add(edge.to());
        }
      }
    }
    return visited;
  }

  /** 环检测（DFS），返回环路径描述；无环返回 {@code null}。 */
  private String findCycle(String startCode, List<WorkflowDefinition.Edge> edges) {
    Set<String> visited = new HashSet<>();
    Set<String> onPath = new LinkedHashSet<>();
    List<String> path = new ArrayList<>();
    String cycle = dfs(startCode, edges, visited, onPath, path);
    return cycle;
  }

  private String dfs(
      String nodeCode,
      List<WorkflowDefinition.Edge> edges,
      Set<String> visited,
      Set<String> onPath,
      List<String> path) {
    visited.add(nodeCode);
    onPath.add(nodeCode);
    path.add(nodeCode);
    for (WorkflowDefinition.Edge edge : edges) {
      if (!edge.from().equals(nodeCode)) {
        continue;
      }
      String next = edge.to();
      if (onPath.contains(next)) {
        int index = path.indexOf(next);
        List<String> cyclePath = new ArrayList<>(path.subList(index, path.size()));
        cyclePath.add(next);
        return String.join(" → ", cyclePath);
      }
      if (!visited.contains(next)) {
        String found = dfs(next, edges, visited, onPath, path);
        if (found != null) {
          return found;
        }
      }
    }
    onPath.remove(nodeCode);
    path.remove(path.size() - 1);
    return null;
  }

  /** 便捷方法：是否可发布。 */
  public boolean isPublishable(WorkflowDefinition definition) {
    return validate(definition).isEmpty();
  }

  /** 便捷方法：指派规则参数的只读视图（供宿主组装任务时使用）。 */
  public Map<String, List<WorkflowDefinition.AssignmentRule>> assignmentsOf(
      WorkflowDefinition definition) {
    return definition == null ? Map.of() : definition.assignments();
  }
}
