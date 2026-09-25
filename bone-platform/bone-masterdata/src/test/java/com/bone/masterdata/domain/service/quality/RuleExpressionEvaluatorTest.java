package com.bone.masterdata.domain.service.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.masterdata.domain.model.quality.DataQualityRule;
import com.bone.masterdata.domain.model.quality.valueobject.RuleName;
import com.bone.masterdata.domain.model.quality.valueobject.RuleSeverity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleExpressionEvaluatorTest {

  private final RuleExpressionEvaluator evaluator = new RuleExpressionEvaluator();

  private static DataQualityRule rule(String type, String expression) {
    return DataQualityRule.create(
        1L, 100L, RuleName.of("编码非空"), type, expression, RuleSeverity.HIGH, null);
  }

  private static RecordFields record(long id, String field, String value) {
    return RecordFields.of(id, Map.of(field, value));
  }

  /** 空白与「整条记录没有该字段」都算缺失——后者同样是数据不完整。 */
  @Test
  void notNullFlagsBlankValuesOnly() {
    RuleEvaluation result =
        evaluator.evaluate(
            rule("NOT_NULL", "field=code"),
            List.of(record(1L, "code", "A"), record(2L, "code", "  "), record(3L, "other", "x")),
            Map.of());

    assertEquals(2, result.violationCount());
    assertEquals(
        List.of(2L, 3L),
        result.violations().stream().map(RuleEvaluation.Violation::recordId).toList());
  }

  @Test
  void uniqueFlagsDuplicatedValuesAndSkipsBlank() {
    RuleEvaluation result =
        evaluator.evaluate(
            rule("UNIQUE", "field=code"),
            List.of(record(1L, "code", "A"), record(2L, "code", "A"), record(3L, "code", "")),
            Map.of());

    assertEquals(2, result.violationCount());
  }

  @Test
  void formatUsesFullMatchRegex() {
    RuleEvaluation result =
        evaluator.evaluate(
            rule("FORMAT", "field=zip;pattern=^\\d{6}$"),
            List.of(
                record(1L, "zip", "518000"), record(2L, "zip", "51800"), record(3L, "zip", "abc")),
            Map.of());

    assertEquals(2, result.violationCount());
  }

  @Test
  void rangeComparesNumericallyAndReportsNonNumeric() {
    RuleEvaluation result =
        evaluator.evaluate(
            rule("RANGE", "field=amount;min=0;max=100"),
            List.of(
                record(1L, "amount", "50"), record(2L, "amount", "101"), record(3L, "amount", "x")),
            Map.of());

    assertEquals(2, result.violationCount());
  }

  @Test
  void referenceChecksAgainstPreloadedValues() {
    RuleEvaluation result =
        evaluator.evaluate(
            rule("REFERENCE", "field=dept;entity=200;targetField=code"),
            List.of(record(1L, "dept", "D1"), record(2L, "dept", "D9")),
            Map.of("200#code", Set.of("D1", "D2")));

    assertEquals(1, result.violationCount());
    assertEquals(2L, result.violations().get(0).recordId());
  }

  /** 未预加载引用取值时必须显式"未求值"，不能当成通过。 */
  @Test
  void referenceWithoutLoadedValuesIsUnsupported() {
    RuleEvaluation result =
        evaluator.evaluate(
            rule("REFERENCE", "field=dept;entity=200"),
            List.of(record(1L, "dept", "D1")),
            Map.of());

    assertTrue(result.isUnsupported());
    assertEquals(0, result.violationCount());
  }

  /** 不受支持的类型（如 CUSTOM 脚本）显式标注未求值——历史上这里曾被 501 整体挡掉。 */
  @Test
  void unsupportedTypeIsExplicitAndNotPassed() {
    RuleEvaluation result =
        evaluator.evaluate(rule("CUSTOM", "x > 1"), List.of(record(1L, "x", "0")), Map.of());

    assertTrue(result.isUnsupported());
    assertTrue(result.unsupportedReason().contains("暂不支持"));
    assertFalse(evaluator.supports("CUSTOM"));
  }

  @Test
  void validateRejectsExpressionsThatCannotBeEvaluated() {
    assertEquals("规则表达式缺少 field=<字段编码>", evaluator.validate("NOT_NULL", "code != null"));
    assertEquals("FORMAT 规则需提供 pattern=<正则表达式>", evaluator.validate("FORMAT", "field=zip"));
    assertNull(evaluator.validate("FORMAT", "field=zip;pattern=^\\d{6}$"));
    assertNull(evaluator.validate("CUSTOM", "anything"), "不支持的类型不阻止录入");
  }

  @Test
  void referenceTargetIsParsedForPreload() {
    var target = evaluator.referenceTargetOf(rule("REFERENCE", "field=dept;entity=200"));
    assertTrue(target.isPresent());
    assertEquals(200L, target.get().entityId());
    assertEquals("dept", target.get().field(), "未声明 targetField 时默认取 field");
  }
}
