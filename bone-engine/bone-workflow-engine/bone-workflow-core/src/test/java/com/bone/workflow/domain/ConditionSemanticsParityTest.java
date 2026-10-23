package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 条件语义黄金样例的契约测试。
 *
 * <p><b>为什么这组样例是「公共契约」</b>：条件求值允许两种实现——引擎自带的 {@link StructuredConditionEvaluator}， 与宿主注入的「与平台 UI
 * 协议规则同源」实现。二者语义一旦分叉，同一张单据会「界面显示走 A 分支、实际走 B 分支」， 且<b>没有任何测试会报</b>。本类用一组共享样例把语义钉死；宿主实现须在自己的测试里跑同一份
 * {@link ConditionSemanticsSamples}（parity 测试）。
 */
class ConditionSemanticsParityTest {

  @Test
  @DisplayName("引擎自带求值器必须逐条满足黄金样例（宿主同源实现须跑同一份样例）")
  void structuredEvaluator_satisfiesGoldenSamples() {
    StructuredConditionEvaluator evaluator = new StructuredConditionEvaluator();
    for (ConditionSemanticsSamples.Sample sample : ConditionSemanticsSamples.samples()) {
      boolean actual =
          evaluator.evaluate(sample.condition(), sample.data(), ConditionContext.of("u1"));
      assertEquals(sample.expected(), actual, "条件语义与黄金样例不一致：" + sample.name());
    }
  }

  @Test
  @DisplayName("黄金样例集必须覆盖正反两侧（缺任一侧都会让漂移漏检）")
  void goldenSamples_coverBothSides() {
    assertTrue(
        ConditionSemanticsSamples.samples().stream()
            .anyMatch(ConditionSemanticsSamples.Sample::expected),
        "样例集须含期望为真的用例");
    assertTrue(
        ConditionSemanticsSamples.samples().stream().anyMatch(sample -> !sample.expected()),
        "样例集须含期望为假的用例（负向样例是防「求值失败当恒真放行」的关键）");
    assertTrue(ConditionSemanticsSamples.samples().size() >= 15, "样例集过少，覆盖不足");
  }
}
