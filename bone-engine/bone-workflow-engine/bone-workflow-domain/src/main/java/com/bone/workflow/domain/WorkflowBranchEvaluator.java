package com.bone.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分支选择器：为 {@link WorkflowDefinition.NodeType#CONDITION} 节点选出唯一后继节点。
 *
 * <p><b>fail-closed 纪律（与 UI 规则的关键差异）</b>：UI 规则里「条件不命中 = 这条规则不适用」，当 false 是安全的；
 * 但审批分支不命中意味着「没有人被选中」——要么单据卡死无人审批，要么退到默认分支走错审批人。故本类一律<b>抛 错</b>而非静默：
 *
 * <ul>
 *   <li>无分支命中且无默认边 → 抛错（拒绝静默放行）
 *   <li>多条分支同时命中 → 抛错（配置歧义，须人工消歧）
 *   <li>条件结构非法（未知算子 / 缺 fieldCode / 嵌套超限）→ 抛错（<b>绝不</b>当 false 处理）
 *   <li>多条默认边、或默认边不在末位 → 抛错
 * </ul>
 *
 * <p>静态检查（{@link #lintCondition}）同时服务于两处：发布期把「算子拼错」拦在发布前；运行期保证「拼错」不会被 当成 false 静默放行。
 */
public final class WorkflowBranchEvaluator {

  private final WorkflowConditionEvaluator conditionEvaluator;

  public WorkflowBranchEvaluator(WorkflowConditionEvaluator conditionEvaluator) {
    this.conditionEvaluator = conditionEvaluator;
  }

  /** 分支（条件节点的一条出边）。 */
  public record Branch(String to, JsonNode when, String label) {

    /** 是否默认边（else）。 */
    public boolean isDefault() {
      return when == null
          || when.isNull()
          || when.isMissingNode()
          || (when.isObject() && when.isEmpty());
    }
  }

  /** 选边结果。 */
  public record Selection(String to, String matchedLabel, boolean defaulted) {}

  /**
   * 条件静态检查：返回问题清单（空 = 合法）。
   *
   * @param condition 条件（结构化 JSON）
   * @param path 问题定位路径（用于错误信息）
   */
  public static List<String> lintCondition(JsonNode condition, String path) {
    List<String> problems = new ArrayList<>();
    lintCondition(condition, path, 0, problems);
    return problems;
  }

  private static void lintCondition(
      JsonNode condition, String path, int depth, List<String> problems) {
    if (condition == null || condition.isNull() || condition.isMissingNode()) {
      return;
    }
    if (depth > StructuredConditionEvaluator.MAX_COND_DEPTH) {
      problems.add(path + ": 条件嵌套超过 " + StructuredConditionEvaluator.MAX_COND_DEPTH + " 层");
      return;
    }
    boolean hasAll = condition.has("all");
    boolean hasAny = condition.has("any");
    boolean hasNot = condition.has("not");
    int kinds = (hasAll ? 1 : 0) + (hasAny ? 1 : 0) + (hasNot ? 1 : 0);
    if (kinds > 1) {
      problems.add(path + ": all/any/not 只能三选一");
      return;
    }
    if (hasAll) {
      for (JsonNode child : condition.path("all")) {
        lintCondition(child, path + ".all", depth + 1, problems);
      }
      return;
    }
    if (hasAny) {
      for (JsonNode child : condition.path("any")) {
        lintCondition(child, path + ".any", depth + 1, problems);
      }
      return;
    }
    if (hasNot) {
      lintCondition(condition.path("not"), path + ".not", depth + 1, problems);
      return;
    }
    String fieldCode = text(condition, "fieldCode");
    String operator = text(condition, "operator");
    if (fieldCode == null || fieldCode.isBlank()) {
      problems.add(path + ": 缺 fieldCode");
      return;
    }
    if (operator == null || operator.isBlank()) {
      problems.add(path + ": 缺 operator");
      return;
    }
    if (!StructuredConditionEvaluator.SUPPORTED_OPERATORS.contains(operator)) {
      problems.add(
          path
              + ": 未知算子 "
              + operator
              + "（支持 "
              + StructuredConditionEvaluator.SUPPORTED_OPERATORS
              + "）");
      return;
    }
    if ("field".equals(text(condition, "valueType"))) {
      String compareField = text(condition, "compareField");
      if (compareField == null || compareField.isBlank()) {
        problems.add(path + ": valueType=field 时必须给 compareField");
      }
      return;
    }
    if ("in".equals(operator) || "notIn".equals(operator) || "between".equals(operator)) {
      JsonNode value = condition.path("value");
      if (!value.isArray()) {
        problems.add(path + ": 算子 " + operator + " 要求 value 为数组");
      } else if ("between".equals(operator) && value.size() != 2) {
        problems.add(path + ": between 要求 value 恰为 2 个元素");
      }
    }
  }

  /**
   * 选边（fail-closed）。
   *
   * @param branches 该条件节点的全部出边（默认边须在末位）
   * @param data 业务数据
   * @param ctx 主体上下文
   * @return 唯一命中的后继节点
   * @throws IllegalStateException 配置歧义 / 条件非法 / 无分支命中且无默认边
   */
  public Selection select(List<Branch> branches, Map<String, Object> data, ConditionContext ctx) {
    if (branches == null || branches.isEmpty()) {
      throw new IllegalStateException("条件节点没有任何出边");
    }
    List<Branch> defaults = branches.stream().filter(Branch::isDefault).toList();
    if (defaults.size() > 1) {
      throw new IllegalStateException("条件节点存在多条默认边（else），配置歧义");
    }
    if (!defaults.isEmpty() && !defaults.get(0).equals(branches.get(branches.size() - 1))) {
      throw new IllegalStateException("默认边（else）必须放在出边末位");
    }
    List<Branch> matched = new ArrayList<>();
    for (Branch branch : branches) {
      if (branch.isDefault()) {
        continue;
      }
      List<String> problems = lintCondition(branch.when(), "when");
      if (!problems.isEmpty()) {
        throw new IllegalStateException("分支条件配置非法：" + String.join("；", problems));
      }
      if (conditionEvaluator.evaluate(branch.when(), data, ctx)) {
        matched.add(branch);
      }
    }
    if (matched.size() > 1) {
      throw new IllegalStateException(
          "条件节点有多条分支同时命中（配置歧义）：" + matched.stream().map(Branch::to).toList());
    }
    if (matched.size() == 1) {
      Branch branch = matched.get(0);
      return new Selection(branch.to(), branch.label(), false);
    }
    if (!defaults.isEmpty()) {
      Branch fallback = defaults.get(0);
      return new Selection(fallback.to(), fallback.label(), true);
    }
    throw new IllegalStateException("条件节点无任何分支命中且无默认边（拒绝静默放行）");
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.path(field);
    return (value.isMissingNode() || value.isNull()) ? null : value.asText();
  }
}
