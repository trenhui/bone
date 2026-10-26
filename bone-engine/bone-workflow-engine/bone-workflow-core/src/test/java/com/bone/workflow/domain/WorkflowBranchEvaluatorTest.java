package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分支选择器的 fail-closed 判别力测试。
 *
 * <p>本类的核心命题是「<b>绝不静默</b>」：条件不命中 / 多条命中 / 条件非法 / 默认边乱序，一律必须抛错——把任一情形 改成静默（取第一条 / 返回 false）都应让本类变红。
 */
class WorkflowBranchEvaluatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final WorkflowBranchEvaluator evaluator =
      new WorkflowBranchEvaluator(new StructuredConditionEvaluator());

  private static JsonNode json(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      out.put(String.valueOf(kv[i]), kv[i + 1]);
    }
    return out;
  }

  private static WorkflowBranchEvaluator.Branch branch(String to, String whenJson, String label) {
    return new WorkflowBranchEvaluator.Branch(to, whenJson == null ? null : json(whenJson), label);
  }

  @Test
  @DisplayName("命中条件分支：返回该分支后继，且非默认回落")
  void selectsMatchingBranch() {
    List<WorkflowBranchEvaluator.Branch> branches =
        List.of(
            branch("big", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}", "大额"),
            branch("small", null, "默认"));
    WorkflowBranchEvaluator.Selection selection =
        evaluator.select(branches, map("amount", 6000), ConditionContext.empty());
    assertEquals("big", selection.to());
    assertFalse(selection.defaulted());
  }

  @Test
  @DisplayName("无分支命中 → 落到默认边（else），并标记 defaulted")
  void fallsBackToDefaultBranch() {
    List<WorkflowBranchEvaluator.Branch> branches =
        List.of(
            branch("big", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}", "大额"),
            branch("small", null, "默认"));
    WorkflowBranchEvaluator.Selection selection =
        evaluator.select(branches, map("amount", 100), ConditionContext.empty());
    assertEquals("small", selection.to());
    assertTrue(selection.defaulted());
  }

  @Test
  @DisplayName("全不命中且无默认边 → 抛错（绝不静默取第一条）")
  void noMatchWithoutDefault_throws() {
    List<WorkflowBranchEvaluator.Branch> branches =
        List.of(
            branch("big", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}", "大额"));
    WorkflowEngineException failure =
        assertThrows(
            WorkflowEngineException.class,
            () -> evaluator.select(branches, map("amount", 100), ConditionContext.empty()));
    assertEquals(
        WorkflowErrorCode.BRANCH_NO_MATCH,
        failure.code(),
        "无匹配必须给稳定错误码，宿主才能按 code.httpStatus() 翻译（而非冒泡成 500）");
  }

  @Test
  @DisplayName("多条分支同时命中 → 抛错（配置歧义不得静默取第一条）")
  void multipleMatches_throws() {
    List<WorkflowBranchEvaluator.Branch> branches =
        List.of(
            branch("a", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":100}", "A"),
            branch("b", "{\"fieldCode\":\"dept\",\"operator\":\"eq\",\"value\":\"RND\"}", "B"),
            branch("c", null, "默认"));
    WorkflowEngineException failure =
        assertThrows(
            WorkflowEngineException.class,
            () ->
                evaluator.select(
                    branches, map("amount", 200, "dept", "RND"), ConditionContext.empty()));
    assertEquals(
        WorkflowErrorCode.BRANCH_AMBIGUOUS, failure.code(), "多条分支同时命中是运行时可达的（条件重叠与数据相关），必须给稳定错误码");
  }

  @Test
  @DisplayName("未知算子 → 抛错（不得当成条件不成立而静默走默认边）")
  void unknownOperator_throws() {
    List<WorkflowBranchEvaluator.Branch> branches =
        List.of(
            branch("a", "{\"fieldCode\":\"amount\",\"operator\":\"hack\",\"value\":1}", "A"),
            branch("b", null, "默认"));
    assertThrows(
        WorkflowEngineException.class,
        () -> evaluator.select(branches, map("amount", 1), ConditionContext.empty()));
  }

  @Test
  @DisplayName("多条默认边 → 抛错（配置歧义）")
  void multipleDefaults_throws() {
    List<WorkflowBranchEvaluator.Branch> branches =
        List.of(branch("a", null, "默认1"), branch("b", null, "默认2"));
    assertThrows(
        WorkflowEngineException.class,
        () -> evaluator.select(branches, map("amount", 1), ConditionContext.empty()));
  }

  @Test
  @DisplayName("默认边不在末位 → 抛错")
  void defaultNotLast_throws() {
    List<WorkflowBranchEvaluator.Branch> branches =
        List.of(
            branch("a", null, "默认"),
            branch("b", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":1}", "B"));
    assertThrows(
        WorkflowEngineException.class,
        () -> evaluator.select(branches, map("amount", 5), ConditionContext.empty()));
  }

  @Test
  @DisplayName("静态检查：缺 fieldCode / 缺 operator / 未知算子 / 非法 in 值 均被列出")
  void lintCondition_reportsProblems() {
    assertTrue(
        WorkflowBranchEvaluator.lintCondition(json("{\"operator\":\"eq\",\"value\":1}"), "c")
            .stream()
            .anyMatch(problem -> problem.contains("fieldCode")));
    assertTrue(
        WorkflowBranchEvaluator.lintCondition(json("{\"fieldCode\":\"a\"}"), "c").stream()
            .anyMatch(problem -> problem.contains("operator")));
    assertTrue(
        WorkflowBranchEvaluator.lintCondition(
                json("{\"fieldCode\":\"a\",\"operator\":\"hack\",\"value\":1}"), "c")
            .stream()
            .anyMatch(problem -> problem.contains("未知算子")));
    assertTrue(
        WorkflowBranchEvaluator.lintCondition(
                json("{\"fieldCode\":\"a\",\"operator\":\"in\",\"value\":1}"), "c")
            .stream()
            .anyMatch(problem -> problem.contains("数组")));
    assertTrue(
        WorkflowBranchEvaluator.lintCondition(
                json("{\"fieldCode\":\"a\",\"operator\":\"eq\",\"value\":1}"), "c")
            .isEmpty());
  }
}
