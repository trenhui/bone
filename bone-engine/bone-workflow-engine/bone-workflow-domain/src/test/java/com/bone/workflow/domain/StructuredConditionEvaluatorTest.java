package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 结构化条件求值器的判别力测试。
 *
 * <p><b>判别力纪律</b>：每个用例都自问「删掉实现哪一行会红」。纯 OK 用例无法证明条件真的在求值，因此本类同时包含 <b>必红的负向用例</b>（条件不成立 → 必须为
 * false）与<b>边界用例</b>（金额容差边界）。
 */
class StructuredConditionEvaluatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final StructuredConditionEvaluator evaluator = new StructuredConditionEvaluator();

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

  @Test
  @DisplayName("数值比较：amount gt 5000 成立 / 不成立两侧都要判别")
  void gt_comparesNumerically() {
    JsonNode cond = json("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}");
    assertTrue(evaluator.evaluate(cond, map("amount", 5000.01), ConditionContext.empty()));
    assertFalse(evaluator.evaluate(cond, map("amount", 5000), ConditionContext.empty()));
    assertFalse(evaluator.evaluate(cond, map("amount", 100), ConditionContext.empty()));
  }

  @Test
  @DisplayName("金额容差：5000 与 5000.004 视为相等（防 DECIMAL 与 double 精度误伤）")
  void eq_honorsMoneyTolerance() {
    JsonNode cond = json("{\"fieldCode\":\"amount\",\"operator\":\"eq\",\"value\":5000}");
    assertTrue(evaluator.evaluate(cond, map("amount", 5000.004), ConditionContext.empty()));
    assertFalse(evaluator.evaluate(cond, map("amount", 5000.006), ConditionContext.empty()));
  }

  @Test
  @DisplayName("布尔条件不得退化为字符串比较（value=true 必须与布尔字段相等）")
  void eq_keepsBooleanNativeType() {
    JsonNode cond = json("{\"fieldCode\":\"urgent\",\"operator\":\"eq\",\"value\":true}");
    assertTrue(evaluator.evaluate(cond, map("urgent", true), ConditionContext.empty()));
    assertFalse(evaluator.evaluate(cond, map("urgent", "true"), ConditionContext.empty()));
  }

  @Test
  @DisplayName("in / notIn：命中集合内为真，集合外为假")
  void in_matchesListMembership() {
    JsonNode cond =
        json("{\"fieldCode\":\"category\",\"operator\":\"in\",\"value\":[\"TRAVEL\",\"MEAL\"]}");
    assertTrue(evaluator.evaluate(cond, map("category", "MEAL"), ConditionContext.empty()));
    assertFalse(evaluator.evaluate(cond, map("category", "OFFICE"), ConditionContext.empty()));
  }

  @Test
  @DisplayName("between：含边界区间")
  void between_includesBounds() {
    JsonNode cond =
        json("{\"fieldCode\":\"amount\",\"operator\":\"between\",\"value\":[1000,5000]}");
    assertTrue(evaluator.evaluate(cond, map("amount", 1000), ConditionContext.empty()));
    assertTrue(evaluator.evaluate(cond, map("amount", 5000), ConditionContext.empty()));
    assertFalse(evaluator.evaluate(cond, map("amount", 5001), ConditionContext.empty()));
  }

  @Test
  @DisplayName("子表路径穿透：items.amount 取到各行金额")
  void pathVal_penetratesChildRows() {
    JsonNode cond = json("{\"fieldCode\":\"items.amount\",\"operator\":\"gt\",\"value\":100}");
    Map<String, Object> data = map("items", List.of(map("amount", 120), map("amount", 80)));
    assertTrue(evaluator.evaluate(cond, data, ConditionContext.empty()));
  }

  @Test
  @DisplayName("all / any / not 组合：任一子条件不成立则 all 为假")
  void compositeConditions() {
    JsonNode all =
        json(
            "{\"all\":[{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000},"
                + "{\"fieldCode\":\"dept\",\"operator\":\"eq\",\"value\":\"RND\"}]}");
    assertTrue(
        evaluator.evaluate(all, map("amount", 6000, "dept", "RND"), ConditionContext.empty()));
    assertFalse(
        evaluator.evaluate(all, map("amount", 6000, "dept", "OPS"), ConditionContext.empty()));
  }

  @Test
  @DisplayName("$me 解析：主体条件用当前操作者，不落业务数据")
  void resolvesMeFromContext() {
    JsonNode cond = json("{\"fieldCode\":\"applicant\",\"operator\":\"eq\",\"value\":\"$me\"}");
    assertTrue(evaluator.evaluate(cond, map("applicant", "u1"), ConditionContext.of("u1")));
    assertFalse(evaluator.evaluate(cond, map("applicant", "u2"), ConditionContext.of("u1")));
  }

  @Test
  @DisplayName("嵌套超深 → 判不成立（fail-closed，不得放行）")
  void tooDeepCondition_isNotSatisfied() {
    JsonNode deep =
        json(
            "{\"all\":[{\"all\":[{\"all\":[{\"all\":[{\"fieldCode\":\"a\",\"operator\":\"eq\",\"value\":1}]}]}]}]}");
    assertFalse(evaluator.evaluate(deep, map("a", 1), ConditionContext.empty()));
  }

  @Test
  @DisplayName("未知算子 → 判不成立（不得当成恒真放行）")
  void unknownOperator_isNotSatisfied() {
    JsonNode cond = json("{\"fieldCode\":\"a\",\"operator\":\"hack\",\"value\":1}");
    assertFalse(evaluator.evaluate(cond, map("a", 1), ConditionContext.empty()));
  }
}
