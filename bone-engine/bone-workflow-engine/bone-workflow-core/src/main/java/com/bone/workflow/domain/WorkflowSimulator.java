package com.bone.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程定义试运行器：给定一份样例业务数据，回答「这张单据会走哪条路、每个分支判成什么、为什么」。
 *
 * <p><b>为什么值得单独做</b>（设计文档 §5.5）：流程定义是业务人员可编辑的元数据，而「条件写反」「兜底边指向错」
 * 这两类错误的共同特征是——发布期语法检查全绿、运行期表现正常（单据照常流转），只是<b>流到了错误的审批人手上</b>。
 * 这类事故靠看日志几乎不可能发现（没有人会拿着一张通过的单据去反问「财务经理本该审吗」）。故把「条件求值」提前到发布前 可视：{@code amount=88000}
 * 输入进去，界面直接显示「AMT→FIN_MGR 命中，AMT→FIN_CHECK 不成立（88000 ≤ 5000 = false）」。
 *
 * <p><b>纯函数、零 IO、零副作用</b>：不建实例、不派单、不落库、不通知。它复用运行期<b>同一套</b> 求值器（{@link WorkflowBranchEvaluator} /
 * {@link WorkflowConditionEvaluator}），因此模拟结论与实际运行结论同源—— 若模拟 另写一套简化逻辑，就退化成「模拟说走 A、实际走 B」，比不做更糟。
 *
 * <p><b>为什么不复用 {@code WorkflowEngineImpl.advance} 的整条链路</b>：那条链路要实例、任务、租户、通知、审计。试运行需要的只是
 * 「沿边走到头」这一半，硬接会把一个纯函数变成需要整套持久化的用例，反而不可测。
 */
public final class WorkflowSimulator {

  /** 单条分支的求值痕迹。 */
  public record BranchTrace(
      String to, String label, boolean defaulted, Boolean matched, List<String> problems) {}

  /** 一个条件节点的求值痕迹（按定义顺序，默认边在末位）。 */
  public record ConditionTrace(String nodeCode, String label, List<BranchTrace> branches) {}

  /**
   * 试运行结果。
   *
   * @param path 途经节点编码（含 START 与 END）
   * @param conditionTraces 途中每个条件节点的分支判定明细
   * @param warnings 非致命提醒（如「无分支命中，已走默认边」）
   * @param error 致命错误（定义非法、无分支命中且无默认边、疑似环路）；{@code null} = 正常走通
   */
  public record Simulation(
      List<String> path,
      List<ConditionTrace> conditionTraces,
      List<String> warnings,
      String error) {

    public Simulation {
      path = path == null ? List.of() : List.copyOf(path);
      conditionTraces = conditionTraces == null ? List.of() : List.copyOf(conditionTraces);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** 是否走通（无致命错误）。 */
    public boolean succeeded() {
      return error == null;
    }
  }

  /** 防环上限：v1 定义禁止回环，超限即说明定义被改坏（或校验被绕过），必须报错而不是转圈。 */
  private static final int MAX_STEPS_FACTOR = 3;

  private final WorkflowBranchEvaluator branchEvaluator;
  private final WorkflowConditionEvaluator conditionEvaluator;

  public WorkflowSimulator(
      WorkflowBranchEvaluator branchEvaluator, WorkflowConditionEvaluator conditionEvaluator) {
    this.branchEvaluator = branchEvaluator;
    this.conditionEvaluator = conditionEvaluator;
  }

  /** 缺省装配（结构化条件求值，与引擎缺省一致）。 */
  public static WorkflowSimulator withDefaults() {
    StructuredConditionEvaluator evaluator = new StructuredConditionEvaluator();
    return new WorkflowSimulator(new WorkflowBranchEvaluator(evaluator), evaluator);
  }

  /**
   * 试运行。
   *
   * @param definition 流程定义（可以是未发布的草稿）
   * @param data 样例业务数据（按 fieldCode 键；支持 {@code a.b} 路径穿透）
   * @param ctx 主体上下文（{@code $me} 等）
   */
  public Simulation simulate(
      WorkflowDefinition definition, Map<String, Object> data, ConditionContext ctx) {
    List<String> path = new ArrayList<>();
    List<ConditionTrace> traces = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Map<String, Object> sample = data == null ? Map.of() : data;
    ConditionContext context = ctx == null ? ConditionContext.empty() : ctx;

    if (definition == null || definition.nodes().isEmpty()) {
      return new Simulation(path, traces, warnings, "流程定义为空，无法试运行");
    }
    List<WorkflowDefinition.Node> starts =
        definition.nodes().stream()
            .filter(node -> node.type() == WorkflowDefinition.NodeType.START)
            .toList();
    if (starts.isEmpty()) {
      return new Simulation(path, traces, warnings, "缺发起节点（START），无法确定入口");
    }
    if (starts.size() > 1) {
      return new Simulation(path, traces, warnings, "存在 " + starts.size() + " 个发起节点（START），入口不唯一");
    }

    String current = starts.get(0).code();
    int budget = definition.nodes().size() * MAX_STEPS_FACTOR + 8;
    for (int step = 0; step < budget; step++) {
      path.add(current);
      WorkflowDefinition.Node node = definition.node(current).orElse(null);
      if (node == null) {
        return new Simulation(path, traces, warnings, "边指向不存在的节点：" + current);
      }
      if (node.type() == WorkflowDefinition.NodeType.END) {
        return new Simulation(path, traces, warnings, null);
      }
      List<WorkflowDefinition.Edge> outs = definition.outEdges(current);
      if (outs.isEmpty()) {
        return new Simulation(path, traces, warnings, "节点 " + current + " 没有后继（流程在此断头），不会到达结束节点");
      }
      if (node.type() == WorkflowDefinition.NodeType.CONDITION) {
        ConditionTrace trace = traceCondition(node, outs, sample, context, warnings);
        traces.add(trace);
        try {
          current = branchEvaluator.select(toBranches(outs), sample, context).to();
        } catch (WorkflowEngineException exception) {
          return new Simulation(
              path, traces, warnings, exception.code() + "：" + exception.getMessage());
        }
        continue;
      }
      if (outs.size() > 1) {
        return new Simulation(
            path,
            traces,
            warnings,
            "节点 " + current + " 有 " + outs.size() + " 条出边，但只有 CONDITION 节点允许多出边（运行期会拒绝推进）");
      }
      WorkflowDefinition.Edge only = outs.get(0);
      if (!only.isDefault()) {
        warnings.add("节点 " + current + " 的出边带条件，但该节点类型不支持条件（运行期忽略此条件）");
      }
      current = only.to();
    }
    return new Simulation(path, traces, warnings, "试运行步数超限（疑似存在环，v1 不支持回环审批）");
  }

  /** 逐分支求值并记录痕迹：命中/不成立/被跳过，以及条件结构本身的问题。 */
  private ConditionTrace traceCondition(
      WorkflowDefinition.Node node,
      List<WorkflowDefinition.Edge> outs,
      Map<String, Object> data,
      ConditionContext ctx,
      List<String> warnings) {
    List<BranchTrace> branches = new ArrayList<>();
    int matchedCount = 0;
    for (WorkflowDefinition.Edge edge : outs) {
      if (edge.isDefault()) {
        branches.add(new BranchTrace(edge.to(), edge.label(), true, null, List.of()));
        continue;
      }
      List<String> problems = WorkflowBranchEvaluator.lintCondition(edge.when(), "条件分支");
      if (!problems.isEmpty()) {
        branches.add(new BranchTrace(edge.to(), edge.label(), false, null, problems));
        continue;
      }
      boolean matched = conditionEvaluator.evaluate(edge.when(), data, ctx);
      if (matched) {
        matchedCount++;
      }
      branches.add(new BranchTrace(edge.to(), edge.label(), false, matched, List.of()));
    }
    if (matchedCount > 1) {
      warnings.add("条件节点 " + node.code() + " 有 " + matchedCount + " 条分支同时命中（配置歧义），运行期会拒绝推进而不是任选一条");
    }
    if (matchedCount == 0) {
      // 兜底边不是「没命中」的同义词：业务方常常以为「默认边 = 永远走不到」，实际它是
      // 字段缺省 / 条件写反时的落点。故这里显式提示，让发布前就能看见「这张单会走默认分支」。
      warnings.add(
          outs.stream().anyMatch(WorkflowDefinition.Edge::isDefault)
              ? "条件节点 " + node.code() + " 无分支命中，将走默认边（else）"
              : "条件节点 " + node.code() + " 无分支命中且无默认边，运行期会拒绝推进");
    }
    return new ConditionTrace(node.code(), node.label(), branches);
  }

  private List<WorkflowBranchEvaluator.Branch> toBranches(List<WorkflowDefinition.Edge> outs) {
    List<WorkflowBranchEvaluator.Branch> branches = new ArrayList<>(outs.size());
    for (WorkflowDefinition.Edge edge : outs) {
      JsonNode when = edge.when();
      branches.add(new WorkflowBranchEvaluator.Branch(edge.to(), when, edge.label()));
    }
    return branches;
  }
}
