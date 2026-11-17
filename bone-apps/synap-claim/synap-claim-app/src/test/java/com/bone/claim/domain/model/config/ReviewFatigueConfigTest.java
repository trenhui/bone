package com.bone.claim.domain.model.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewFatigueConfigTest {

  private static Map<String, Object> cfg(Object max, Object rest, Object shift, Object amount) {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("maxPerShift", max);
    raw.put("restPromptMinutes", rest);
    raw.put("shiftEndExclusionMinutes", shift);
    raw.put("sensitiveAmountYuan", amount);
    return raw;
  }

  @Test
  @DisplayName("正向：四个键均合法时原样生效，无 rejected")
  void validConfigApplies() {
    ReviewFatigueConfig c = ReviewFatigueConfig.from(cfg(3, 90, 45, "20000"));
    assertThat(c.maxPerShift()).isEqualTo(3);
    assertThat(c.restPromptMinutes()).isEqualTo(90);
    assertThat(c.shiftEndExclusionMinutes()).isEqualTo(45);
    assertThat(c.sensitiveAmountYuan()).isEqualTo(20000L);
    assertThat(c.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("基线：120 / 120 / 30 / 10000")
  void defaultsAreExpected() {
    assertThat(ReviewFatigueConfig.defaults())
        .isEqualTo(new ReviewFatigueConfig(120, 120, 30, 10000L, Map.of()));
    assertThat(ReviewFatigueConfig.from(null)).isEqualTo(ReviewFatigueConfig.defaults());
  }

  @Test
  @DisplayName("缺键：逐键回落基线并登记 rejected，不静默生效")
  void missingKeyFallsBackWithRejection() {
    ReviewFatigueConfig c = ReviewFatigueConfig.from(Map.of("maxPerShift", 5));
    assertThat(c.maxPerShift()).isEqualTo(5);
    assertThat(c.restPromptMinutes()).isEqualTo(120);
    assertThat(c.rejected()).containsKey("restPromptMinutes");
    assertThat(c.fullyApplied()).isFalse();
  }

  @Test
  @DisplayName("越界：maxPerShift=0 回落 120 且给出原因")
  void zeroMaxFallsBack() {
    ReviewFatigueConfig c = ReviewFatigueConfig.from(cfg(0, 120, 30, 10000));
    assertThat(c.maxPerShift()).isEqualTo(ReviewFatigueConfig.DEFAULT_MAX_PER_SHIFT);
    assertThat(c.rejected().get("maxPerShift")).contains("越界").contains("120");
  }

  @Test
  @DisplayName("非法类型：非整数不抛异常，回落基线")
  void nonIntegerFallsBack() {
    ReviewFatigueConfig c = ReviewFatigueConfig.from(cfg("many", 120, 30, 10000));
    assertThat(c.maxPerShift()).isEqualTo(120);
    assertThat(c.rejected().get("maxPerShift")).contains("非整数");
  }

  @Test
  @DisplayName("小数不允许当整数采信（静默取整 = 配了不生效）")
  void fractionalIsRejected() {
    ReviewFatigueConfig c = ReviewFatigueConfig.from(cfg(1.5d, 120, 30, 10000));
    assertThat(c.maxPerShift()).isEqualTo(120);
    assertThat(c.rejected()).containsKey("maxPerShift");
  }

  @Test
  @DisplayName("未知键必须出现在 rejected，避免配了不生效")
  void unknownKeyRejected() {
    Map<String, Object> raw = cfg(120, 120, 30, 10000);
    raw.put("mysteryKnob", true);
    assertThat(ReviewFatigueConfig.from(raw).rejected()).containsKey("mysteryKnob");
  }

  @Test
  @DisplayName("敏感金额边界：等于阈值即命中提示，不进入资金结论")
  void sensitiveBoundary() {
    ReviewFatigueConfig c = ReviewFatigueConfig.defaults();
    assertThat(c.sensitiveAmountReached(new BigDecimal("9999.99"))).isFalse();
    assertThat(c.sensitiveAmountReached(new BigDecimal("10000"))).isTrue();
    assertThat(c.sensitiveAmountReached(null)).isFalse();
  }
}
