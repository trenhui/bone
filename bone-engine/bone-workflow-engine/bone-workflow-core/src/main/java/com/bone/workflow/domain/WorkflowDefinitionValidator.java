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
 *
 * <p><b>违规定位（设计器契约）</b>：{@link Violation#locator()} 携带出问题的节点编码，建模界面据此把清单项高亮到画布对应节点。 没有定位信息的违规（如
 * WF-000/002/003）表示「问题在整张图」而非某个节点。前端<b>不得</b>靠解析 message 文本来反推 节点——那会让文案改动 变成前端故障。
 */
public final class WorkflowDefinitionValidator {

  /**
   * 一条违规。
   *
   * @param code 稳定错误码（{@code WF-xxx}），前端据此做文案映射与分类，<b>不得</b>依赖 message 文本
   * @param message 面向人的说明（含节点编码，便于日志与工单直接可读）
   * @param locator 出问题的节点编码；{@code null} 表示整图级问题
   */
  public record Violation(String code, String message, String locator) {

    /** 兼容既有调用方与测试：无定位信息的违规。 */
    public Violation(String code, String message) {
      this(code, message, null);
    }

    /** 是否可定位到具体节点（建模界面据此决定能否点击跳转）。 */
    public boolean isNodeScoped() {
      return locator != null && !locator.isBlank();
    }
  }

  /**
   * 指派策略的必需参数（键名与 {@code RecordFieldApproverResolver} 的读取口径逐字对应）。
   *
   * <p><b>为什么发布期必须查参数</b>（诊断 2026-10-27）：运行期 {@code resolve} 对参数缺失一律返回空列表 ⇒ 引擎判「解析不出人」 ⇒
   * 节点<b>挂起</b>。此前只校验策略名在白名单内（WF-013），于是「策略写了 ROLE、忘了填 roleCode」能发布成功、 上线后单据卡死无人审批，而
   * 发布时的校验清单全绿——这是「配置了但不生效」最阴的形态，必须在发布期 fail-closed。
   *
   * <p>键名易错点：{@code USER} 策略读的是<b>单数</b> {@code userId}（多条规则 = 多个人），写成 {@code userIds}
   * 同样会静默挂起，故一并在此拦。
   */
  private static final Map<String, String> REQUIRED_STRATEGY_PARAMS =
      Map.of(
          "ROLE", "roleCode",
          "USER", "userId",
          "FIELD", "fieldCode",
          "INITIATOR_SELECT", "fieldCode");

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

    // —— 引擎格式版本兼容性（诊断 P2-3）——
    // 定义在发布期已校验过，但引擎不假设调用方一定走发布流程（v1 定义由迁移/种子落库）。
    // 格式版本高于引擎可解释上限 ⇒ 引擎太旧，必须 fail-loud 让调用方升级引擎或回退定义；
    // 低于引擎仍兼容下限 ⇒ 该格式已被废弃，静默加载会跑到不兼容的求值路径上。
    int schema = definition.engineSchemaVersion();
    if (schema > WorkflowDefinition.CURRENT_ENGINE_SCHEMA_VERSION) {
      violations.add(
          new Violation(
              "WF-017",
              "流程定义格式版本 "
                  + schema
                  + " 高于当前引擎可解释的最高版本 "
                  + WorkflowDefinition.CURRENT_ENGINE_SCHEMA_VERSION
                  + "：请升级引擎或回退定义（否则加载/求值得跑在不兼容结构上）"));
    } else if (schema < WorkflowDefinition.MIN_SUPPORTED_ENGINE_SCHEMA_VERSION) {
      violations.add(
          new Violation(
              "WF-017",
              "流程定义格式版本 "
                  + schema
                  + " 低于引擎仍兼容的最低版本 "
                  + WorkflowDefinition.MIN_SUPPORTED_ENGINE_SCHEMA_VERSION
                  + "：该格式已废弃，引擎不再支持"));
    }

    // —— 节点基本形态 ——
    Set<String> nodeCodes = new LinkedHashSet<>();
    for (WorkflowDefinition.Node node : nodes) {
      if (node.code() == null || node.code().isBlank()) {
        violations.add(new Violation("WF-001", "存在缺编码的节点"));
        continue;
      }
      if (!nodeCodes.add(node.code())) {
        violations.add(new Violation("WF-001", "节点编码重复：" + node.code(), node.code()));
      }
      if (node.type() == null) {
        violations.add(new Violation("WF-001", "节点 " + node.code() + " 缺类型", node.code()));
        continue;
      }
      if (node.type() == WorkflowDefinition.NodeType.APPROVE && node.mode() == null) {
        violations.add(
            new Violation(
                "WF-012", "审批节点 " + node.code() + " 缺完成判定方式（单签/会签/或签/比例会签）", node.code()));
      }
      if (node.type() == WorkflowDefinition.NodeType.APPROVE
          && node.mode() == WorkflowDefinition.ApprovalMode.RATIO) {
        if (node.quorum() == null || node.quorum() < 1) {
          violations.add(
              new Violation(
                  "WF-019",
                  "比例会签节点 " + node.code() + " 必须指定法定人数 quorum（≥ 1，表示「至少 N 人通过即通过」）",
                  node.code()));
        }
      }
      if (node.quorum() != null && node.mode() != WorkflowDefinition.ApprovalMode.RATIO) {
        violations.add(
            new Violation(
                "WF-019",
                "节点 " + node.code() + " 设置了法定人数 quorum，但完成判定方式不是比例会签（RATIO），quorum 将被忽略",
                node.code()));
      }
      if (node.type() != WorkflowDefinition.NodeType.APPROVE && node.mode() != null) {
        violations.add(
            new Violation("WF-012", "非审批节点 " + node.code() + " 不应设置完成判定方式", node.code()));
      }
    }

    // —— 边形态：引用的节点必须存在 ——
    List<WorkflowDefinition.Edge> validEdges = new ArrayList<>();
    for (WorkflowDefinition.Edge edge : edges) {
      if (!nodeCodes.contains(edge.from()) || !nodeCodes.contains(edge.to())) {
        violations.add(
            new Violation("WF-008", "边引用了不存在的节点：" + edge.from() + " → " + edge.to(), edge.from()));
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
          violations.add(new Violation("WF-004", "结束节点 " + node.code() + " 不应有后继", node.code()));
        }
        continue;
      }
      if (outs.isEmpty()) {
        violations.add(new Violation("WF-005", "节点 " + node.code() + " 没有后继（流程在此断头）", node.code()));
      }
      if (node.type() == WorkflowDefinition.NodeType.CONDITION) {
        lintConditionNode(node, outs, violations);
      }
      if (node.type() == WorkflowDefinition.NodeType.APPROVE) {
        lintApprovalNode(node, definition.assignments().get(node.code()), violations);
        lintSingleOutEdge(node, outs, violations, "WF-014", "审批节点");
      }
      if (node.type() == WorkflowDefinition.NodeType.START) {
        lintSingleOutEdge(node, outs, violations, "WF-015", "发起节点");
      }
      if (node.type() == WorkflowDefinition.NodeType.CC) {
        // 抄送已落地：改为按「必须有抄送人指派规则 + 单一出边」校验，不再一律拒绝。
        // 抄送人沿用与审批节点同一套指派规则（ROLE/USER/LINE_MANAGER/DEPT_HEAD/LEVEL_UP/FIELD），
        // 只是解析结果用于「知会」而非「建待办」。
        List<WorkflowDefinition.AssignmentRule> ccRules =
            definition.assignments().getOrDefault(node.code(), List.of());
        if (ccRules.isEmpty()) {
          violations.add(new Violation("WF-018", "抄送节点 " + node.code() + " 缺抄送人指派规则", node.code()));
        } else {
          lintAssignmentParams(node, ccRules, violations);
        }
        lintSingleOutEdge(node, outs, violations, "WF-018", "抄送节点");
      }
    }

    // —— 同一对节点的重复边（歧义）——
    Set<String> edgeKeys = new HashSet<>();
    for (WorkflowDefinition.Edge edge : validEdges) {
      String key = edge.from() + "->" + edge.to();
      if (!edgeKeys.add(key)) {
        violations.add(new Violation("WF-011", "存在重复边：" + key, edge.from()));
      }
    }

    // —— 可达性 + 环检测（均以唯一 START 为前提）——
    if (starts.size() == 1) {
      String startCode = starts.get(0).code();
      Set<String> reachable = reachableFrom(startCode, validEdges);
      for (WorkflowDefinition.Node node : nodes) {
        if (node.code() != null && !reachable.contains(node.code())) {
          violations.add(
              new Violation("WF-009", "节点 " + node.code() + " 从发起节点不可达（孤岛节点）", node.code()));
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
      violations.add(
          new Violation("WF-006", "条件节点 " + node.code() + " 至少需要 2 条出边（分支 + 默认）", node.code()));
    }
    List<WorkflowDefinition.Edge> defaults =
        outs.stream().filter(WorkflowDefinition.Edge::isDefault).toList();
    if (defaults.isEmpty()) {
      violations.add(new Violation("WF-006", "条件节点 " + node.code() + " 缺默认边（else）", node.code()));
    } else if (defaults.size() > 1) {
      violations.add(
          new Violation("WF-006", "条件节点 " + node.code() + " 存在多条默认边（else）", node.code()));
    } else if (!defaults.get(0).equals(outs.get(outs.size() - 1))) {
      violations.add(
          new Violation("WF-006", "条件节点 " + node.code() + " 的默认边（else）必须放在末位", node.code()));
    }
    for (WorkflowDefinition.Edge edge : outs) {
      if (edge.isDefault()) {
        continue;
      }
      for (String problem : WorkflowBranchEvaluator.lintCondition(edge.when(), "条件分支")) {
        violations.add(
            new Violation(
                "WF-006",
                "条件节点 " + node.code() + " 的出边 " + edge.to() + "：" + problem,
                node.code()));
      }
    }
  }

  /** 审批节点：必须配指派规则，策略须在白名单内，且策略必需参数不得缺失。 */
  private void lintApprovalNode(
      WorkflowDefinition.Node node,
      List<WorkflowDefinition.AssignmentRule> rules,
      List<Violation> violations) {
    if (rules == null || rules.isEmpty()) {
      violations.add(new Violation("WF-007", "审批节点 " + node.code() + " 未配置审批人指派规则", node.code()));
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
                    + "）",
                node.code()));
      }
    }
    lintAssignmentParams(node, rules, violations);
  }

  /**
   * 指派规则参数完整性（WF-020）。
   *
   * <p>校验的是<b>运行期真正会被读取的键</b>，而不是「看起来像参数」的任意键：{@link #REQUIRED_STRATEGY_PARAMS} 与 {@code
   * RecordFieldApproverResolver} 的 switch 分支一一对应。未知策略不在此判（WF-013 已拦）。
   */
  private void lintAssignmentParams(
      WorkflowDefinition.Node node,
      List<WorkflowDefinition.AssignmentRule> rules,
      List<Violation> violations) {
    for (WorkflowDefinition.AssignmentRule rule : rules) {
      if (rule == null || rule.strategy() == null) {
        continue;
      }
      String required = REQUIRED_STRATEGY_PARAMS.get(rule.strategy());
      if (required != null) {
        Object value = rule.params() == null ? null : rule.params().get(required);
        if (value == null || String.valueOf(value).isBlank()) {
          violations.add(
              new Violation(
                  "WF-020",
                  "节点 "
                      + node.code()
                      + " 的指派策略 "
                      + rule.strategy()
                      + " 缺必需参数 "
                      + required
                      + "：运行期解析不出审批人，节点会挂起（不派单、不报错）",
                  node.code()));
        }
        continue;
      }
      if ("LEVEL_UP".equals(rule.strategy())) {
        Object level = rule.params() == null ? null : rule.params().get("level");
        if (level != null && (!(level instanceof Number number) || number.intValue() < 1)) {
          violations.add(
              new Violation(
                  "WF-020",
                  "节点 " + node.code() + " 的指派策略 LEVEL_UP 参数 level 必须是 ≥ 1 的整数，实为：" + level,
                  node.code()));
        }
      }
    }
  }

  /**
   * 单出口节点（审批 / 发起）：必须恰有 1 条出边，且该边不得带条件。
   *
   * <p><b>为什么发布期就要拦</b>：运行期把这类节点的出边当<b>无条件单边</b>消费（{@code WorkflowEngineImpl.completeTask} 要求审批节点恰有
   * 1 条出边，否则 {@code DEFINITION_MALFORMED}；{@code singleOutEdge} 直接取唯一出边）。把条件写在审批/发起节点上，发布期不拦 ⇒
   * 运行期要么炸、要么<b>静默忽略条件走错分支</b>—— 后者无任何报错，是最危险的事故形态。条件分支只能经 {@code CONDITION} 节点表达。
   */
  private void lintSingleOutEdge(
      WorkflowDefinition.Node node,
      List<WorkflowDefinition.Edge> outs,
      List<Violation> violations,
      String code,
      String nodeKind) {
    if (outs.size() > 1) {
      violations.add(
          new Violation(
              code,
              nodeKind
                  + " "
                  + node.code()
                  + " 应恰有 1 条出边，实有 "
                  + outs.size()
                  + " 条（条件分支请使用 CONDITION 节点）",
              node.code()));
      return;
    }
    if (outs.size() == 1 && !outs.get(0).isDefault()) {
      violations.add(
          new Violation(
              code,
              nodeKind + " " + node.code() + " 的唯一出边不应带条件（运行期会忽略该条件；条件分支请使用 CONDITION 节点）",
              node.code()));
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
