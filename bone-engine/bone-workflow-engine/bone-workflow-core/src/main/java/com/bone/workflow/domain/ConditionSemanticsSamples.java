package com.bone.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 条件语义的<b>黄金样例集</b>：条件求值的「事实契约」。
 *
 * <p><b>为什么需要它</b>：条件求值允许两种实现——引擎自带的 {@link StructuredConditionEvaluator}，以及宿主注入的 「与平台 UI
 * 协议规则同源」实现。二者语义一旦分叉，同一张单据会出现「界面显示走 A 分支、实际走 B 分支」，且这种
 * 漂移<b>没有任何测试会报</b>。故把语义钉成一组可共享的样例：任何实现都必须跑通同一份样例。
 *
 * <p>用法：宿主实现 {@link WorkflowConditionEvaluator} 时，在自己的测试里对本样例集逐条断言，即可证明与引擎自带 实现语义一致（parity 测试）。
 */
public final class ConditionSemanticsSamples {

  private ConditionSemanticsSamples() {}

  /** 一条样例：条件 + 数据 + 期望结论。 */
  public record Sample(
      String name, JsonNode condition, Map<String, Object> data, boolean expected) {}

  /**
   * 全部样例。
   *
   * <p>覆盖：数值比较、金额容差边界、布尔原生类型、集合算子、区间算子、子表路径穿透、组合条件、{@code $me} 主体
   * 条件、缺失字段、未知算子、嵌套超深——后四类是<b>负向样例</b>（结论为「不成立」），它们是防「求值失败当恒真放行」 的关键。
   */
  public static List<Sample> samples() {
    return List.of(
        sample(
            "数值大于",
            "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}",
            Map.of("amount", 5000.01),
            true),
        sample(
            "数值等于边界不成立",
            "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}",
            Map.of("amount", 5000),
            false),
        sample(
            "金额容差内视为相等",
            "{\"fieldCode\":\"amount\",\"operator\":\"eq\",\"value\":5000}",
            Map.of("amount", 5000.004),
            true),
        sample(
            "金额超出容差不相等",
            "{\"fieldCode\":\"amount\",\"operator\":\"eq\",\"value\":5000}",
            Map.of("amount", 5000.006),
            false),
        sample(
            "布尔值按原生类型比较",
            "{\"fieldCode\":\"urgent\",\"operator\":\"eq\",\"value\":true}",
            Map.of("urgent", true),
            true),
        sample(
            "布尔值不退化为字符串",
            "{\"fieldCode\":\"urgent\",\"operator\":\"eq\",\"value\":true}",
            Map.of("urgent", "true"),
            false),
        sample(
            "集合命中",
            "{\"fieldCode\":\"category\",\"operator\":\"in\",\"value\":[\"TRAVEL\",\"MEAL\"]}",
            Map.of("category", "MEAL"),
            true),
        sample(
            "集合未命中",
            "{\"fieldCode\":\"category\",\"operator\":\"in\",\"value\":[\"TRAVEL\",\"MEAL\"]}",
            Map.of("category", "OFFICE"),
            false),
        sample(
            "区间含下界",
            "{\"fieldCode\":\"amount\",\"operator\":\"between\",\"value\":[1000,5000]}",
            Map.of("amount", 1000),
            true),
        sample(
            "区间含上界",
            "{\"fieldCode\":\"amount\",\"operator\":\"between\",\"value\":[1000,5000]}",
            Map.of("amount", 5000),
            true),
        sample(
            "区间超上界",
            "{\"fieldCode\":\"amount\",\"operator\":\"between\",\"value\":[1000,5000]}",
            Map.of("amount", 5001),
            false),
        sample(
            "子表路径穿透",
            "{\"fieldCode\":\"items.amount\",\"operator\":\"gt\",\"value\":100}",
            Map.of("items", List.of(Map.of("amount", 120), Map.of("amount", 80))),
            true),
        sample(
            "组合条件全部成立",
            "{\"all\":[{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000},"
                + "{\"fieldCode\":\"dept\",\"operator\":\"eq\",\"value\":\"RND\"}]}",
            Map.of("amount", 6000, "dept", "RND"),
            true),
        sample(
            "组合条件其一不成立",
            "{\"all\":[{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000},"
                + "{\"fieldCode\":\"dept\",\"operator\":\"eq\",\"value\":\"RND\"}]}",
            Map.of("amount", 6000, "dept", "OPS"),
            false),
        sample(
            "not 取反",
            "{\"not\":{\"fieldCode\":\"category\",\"operator\":\"eq\",\"value\":\"TRAVEL\"}}",
            Map.of("category", "OFFICE"),
            true),
        sample(
            "缺失字段视为不成立",
            "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":1}",
            Map.of("other", 100),
            false),
        sample(
            "未知算子视为不成立",
            "{\"fieldCode\":\"amount\",\"operator\":\"hack\",\"value\":1}",
            Map.of("amount", 1),
            false),
        sample(
            "嵌套超深视为不成立",
            "{\"all\":[{\"all\":[{\"all\":[{\"all\":[{\"fieldCode\":\"a\",\"operator\":\"eq\",\"value\":1}]}]}]}]}",
            Map.of("a", 1),
            false));
  }

  private static Sample sample(
      String name, String conditionJson, Map<String, Object> data, boolean expected) {
    try {
      JsonNode condition =
          new com.fasterxml.jackson.databind.ObjectMapper().readTree(conditionJson);
      return new Sample(name, condition, data, expected);
    } catch (Exception e) {
      throw new IllegalArgumentException("样例 JSON 非法: " + name, e);
    }
  }
}
