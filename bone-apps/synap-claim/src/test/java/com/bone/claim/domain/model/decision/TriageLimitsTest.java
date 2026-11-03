package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分诊阈值外置的单测（§8.3 / §11.4）。
 *
 * <p><b>被测的不是「能解析 JSON」，而是三条安全属性</b>：
 *
 * <ol>
 *   <li><b>零配置零变更</b>：不配或配成空，生效阈值必须逐位等于代码基线——否则「上线本特性」本身就是一次静默的策略变更；
 *   <li><b>只能更严</b>：放宽类配置（调高金额上限、调低置信度门槛）必须被拒且回落基线——这是资金敞口的单向棘轮；
 *   <li><b>拒绝要可见</b>：任何未生效的键都必须进 {@code rejected} 并带后果说明——静默忽略等于把「配了不生效」制度化。
 * </ol>
 */
class TriageLimitsTest {

  private static Map<String, Object> config(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  @DisplayName("无配置：四个阈值全部等于代码基线，且无拒绝项")
  void defaultsWithoutConfig() {
    TriageLimits.Resolution r = TriageLimits.apply(Map.of());
    assertThat(r.limits().autoAmountLimit()).isEqualByComparingTo("10000");
    assertThat(r.limits().aiManualThreshold()).isEqualByComparingTo("0.7");
    assertThat(r.limits().aiReviewThreshold()).isEqualByComparingTo("0.85");
    assertThat(r.limits().ocrManualThreshold()).isEqualByComparingTo("0.7");
    assertThat(r.fullyApplied()).isTrue();
    assertThat(r.rejected()).isEmpty();
  }

  @Test
  @DisplayName("null 配置等同无配置（不是「空对象当全禁」）")
  void nullConfigEqualsDefaults() {
    assertThat(TriageLimits.apply(null).limits()).isEqualTo(TriageLimits.defaults());
  }

  @Test
  @DisplayName("收紧金额上限：9000 生效（自动通过变难，属安全方向）")
  void tightenAmountTakesEffect() {
    TriageLimits.Resolution r =
        TriageLimits.apply(config(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT, "9000"));
    assertThat(r.limits().autoAmountLimit()).isEqualByComparingTo("9000");
    assertThat(r.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("放宽金额上限：12000 被拒并回落基线（扩大资金敞口必须走代码发布）")
  void loosenAmountRejected() {
    TriageLimits.Resolution r =
        TriageLimits.apply(config(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT, "12000"));
    assertThat(r.limits().autoAmountLimit()).isEqualByComparingTo("10000");
    assertThat(r.fullyApplied()).isFalse();
    assertThat(r.rejected()).containsKey(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT);
    // 原因必须说清后果与正确做法，而不是只说"不合法"
    assertThat(r.rejected().get(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT))
        .contains("不得高于代码基线")
        .contains("代码发布");
  }

  @Test
  @DisplayName("调高置信度门槛生效（更多案件转人工 = 更严）")
  void tightenConfidenceTakesEffect() {
    TriageLimits.Resolution r =
        TriageLimits.apply(
            config(
                TriageLimits.CONFIG_AI_MANUAL_THRESHOLD, "0.8",
                TriageLimits.CONFIG_AI_REVIEW_THRESHOLD, "0.95"));
    assertThat(r.limits().aiManualThreshold()).isEqualByComparingTo("0.8");
    assertThat(r.limits().aiReviewThreshold()).isEqualByComparingTo("0.95");
    assertThat(r.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("调低置信度门槛被拒（更多案件自动通过 = 放宽）")
  void loosenConfidenceRejected() {
    TriageLimits.Resolution r =
        TriageLimits.apply(config(TriageLimits.CONFIG_AI_MANUAL_THRESHOLD, "0.6"));
    assertThat(r.limits().aiManualThreshold()).isEqualByComparingTo("0.7");
    assertThat(r.fullyApplied()).isFalse();
    assertThat(r.rejected().get(TriageLimits.CONFIG_AI_MANUAL_THRESHOLD)).contains("不得低于代码基线");
  }

  @Test
  @DisplayName("未知键被登记（拼错键不会静默失效）")
  void unknownKeyRejected() {
    TriageLimits.Resolution r =
        TriageLimits.apply(config("autoAmmountLimit", "9000", "ocrMin", "0.9"));
    assertThat(r.fullyApplied()).isFalse();
    assertThat(r.rejected()).containsKeys("autoAmmountLimit", "ocrMin");
    assertThat(r.rejected().get("autoAmmountLimit")).contains("未知键").contains("未生效");
    // 未知键不影响其他合法项
    assertThat(r.limits().autoAmountLimit()).isEqualByComparingTo("10000");
  }

  @Test
  @DisplayName("金额非正数被拒（≤0 会让所有案件都落在金额闸之上）")
  void nonPositiveAmountRejected() {
    for (String v : new String[] {"0", "-1"}) {
      TriageLimits.Resolution r =
          TriageLimits.apply(config(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT, v));
      assertThat(r.limits().autoAmountLimit()).as("value=%s", v).isEqualByComparingTo("10000");
      assertThat(r.rejected()).as("value=%s", v).containsKey(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT);
    }
  }

  @Test
  @DisplayName("置信度越出 (0,1] 被拒（比例越界即判据失效）")
  void confidenceOutOfRangeRejected() {
    for (String v : new String[] {"0", "1.5", "-0.2"}) {
      TriageLimits.Resolution r =
          TriageLimits.apply(config(TriageLimits.CONFIG_OCR_MANUAL_THRESHOLD, v));
      assertThat(r.limits().ocrManualThreshold()).as("value=%s", v).isEqualByComparingTo("0.7");
      assertThat(r.rejected())
          .as("value=%s", v)
          .containsKey(TriageLimits.CONFIG_OCR_MANUAL_THRESHOLD);
    }
  }

  @Test
  @DisplayName("人工线高于复核线上界时冲突项回落基线，另一项仍生效")
  void manualAboveReviewFallsBackWithoutKillingOtherKeys() {
    // aiManual=0.9 越过了复核线上界 0.85 ⇒ 复核档不可达（全部 <0.9 直转人工）
    TriageLimits.Resolution r =
        TriageLimits.apply(
            config(
                TriageLimits.CONFIG_AI_MANUAL_THRESHOLD,
                "0.9",
                TriageLimits.CONFIG_OCR_MANUAL_THRESHOLD,
                "0.8"));
    assertThat(r.limits().aiManualThreshold()).isEqualByComparingTo("0.7");
    assertThat(r.rejected()).containsKey(TriageLimits.CONFIG_AI_MANUAL_THRESHOLD);
    // 另一项不该被牵连（否则一个键配错会让整组配置失效）
    assertThat(r.limits().ocrManualThreshold()).isEqualByComparingTo("0.8");
  }

  @Test
  @DisplayName("终态自洽：任何输入下人工线都不高于复核线上界")
  void alwaysSelfConsistent() {
    // 两个键往同一侧挤：aiManual 抬到 0.85（合法上界），aiReview 也抬到 0.9（合法），仍需满足 manual ≤ review
    TriageLimits.Resolution r =
        TriageLimits.apply(
            config(
                TriageLimits.CONFIG_AI_MANUAL_THRESHOLD, "0.85",
                TriageLimits.CONFIG_AI_REVIEW_THRESHOLD, "0.9"));
    assertThat(r.limits().aiManualThreshold()).isLessThanOrEqualTo(r.limits().aiReviewThreshold());

    TriageLimits.Resolution r2 =
        TriageLimits.apply(
            config(
                TriageLimits.CONFIG_AI_MANUAL_THRESHOLD, "0.95",
                TriageLimits.CONFIG_AI_REVIEW_THRESHOLD, "0.86"));
    assertThat(r2.limits().aiManualThreshold())
        .isLessThanOrEqualTo(r2.limits().aiReviewThreshold());
  }

  @Test
  @DisplayName("非数字值不生效且被登记（不能被当成 0 或缺失静默采信）")
  void nonNumericRejected() {
    TriageLimits.Resolution r =
        TriageLimits.apply(config(TriageLimits.CONFIG_AI_REVIEW_THRESHOLD, "high"));
    assertThat(r.limits().aiReviewThreshold()).isEqualByComparingTo("0.85");
    assertThat(r.rejected()).containsKey(TriageLimits.CONFIG_AI_REVIEW_THRESHOLD);
    assertThat(r.rejected().get(TriageLimits.CONFIG_AI_REVIEW_THRESHOLD))
        .contains("不是合法数值")
        .contains("0.85");
  }

  @Test
  @DisplayName("分诊按生效阈值挪动档位：金额 9000 + 上限配置 8000 ⇒ 落复核档")
  void triageUsesConfiguredAmountLimit() {
    TriageLimits tightened =
        TriageLimits.apply(config(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT, "8000")).limits();
    DecisionTriage.Input in =
        new DecisionTriage.Input(
            true,
            true,
            false,
            new BigDecimal("9000"),
            new BigDecimal("0.9"),
            new BigDecimal("0.9"),
            false,
            true,
            false);
    // 基线（10000）下 9000 可自动通过
    assertThat(DecisionTriage.evaluate(in).result()).isEqualTo(DecisionResult.AUTO_APPROVE);
    // 收紧到 8000 后落复核档
    DecisionTriage.Outcome out = DecisionTriage.evaluate(in, tightened);
    assertThat(out.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(out.tier()).isEqualTo(DecisionTier.REVIEW);
    assertThat(out.reason()).isEqualTo("AMOUNT_OVER_LIMIT");
  }

  @Test
  @DisplayName("未配置时 evaluate(in, defaults) 与 evaluate(in) 结论完全一致（零行为变更钉子）")
  void defaultsAreBehaviorallyIdentical() {
    DecisionTriage.Input[] cases = {
      new DecisionTriage.Input(
          true,
          true,
          false,
          new BigDecimal("500"),
          new BigDecimal("0.9"),
          new BigDecimal("0.9"),
          false,
          true,
          false),
      new DecisionTriage.Input(
          true,
          true,
          false,
          new BigDecimal("9999.99"),
          new BigDecimal("0.7"),
          new BigDecimal("0.7"),
          false,
          true,
          false),
      new DecisionTriage.Input(
          true,
          true,
          false,
          new BigDecimal("10000"),
          new BigDecimal("0.9"),
          new BigDecimal("0.9"),
          false,
          true,
          false),
      new DecisionTriage.Input(
          true,
          true,
          false,
          new BigDecimal("100"),
          new BigDecimal("0.69"),
          new BigDecimal("0.9"),
          false,
          true,
          false),
    };
    for (DecisionTriage.Input in : cases) {
      assertThat(DecisionTriage.evaluate(in, TriageLimits.defaults()))
          .isEqualTo(DecisionTriage.evaluate(in));
    }
  }
}
