package com.bone.claim.domain.model.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 抽检配置值对象测试（V1.0 100% 全量覆盖的配置口径）。
 *
 * <p>钉住三件事：① 默认回落基线 5%（不是 0 不抽、也不是 100 全量）；② 显式配置 100 时全量生效；③ 任何非法值 （空/null/非整数/越界）都回落 5% 且在 {@code
 * rejected} 登记原因，让「配了不生效」可见而非静默。
 *
 * <p><b>变异可证伪</b>：把 {@code DEFAULT_SAMPLING_RATE} 从 5 改成 0（移除 fail-safe 语义）， {@code
 * defaultsIsFivePercent} 与所有回落断言必红；还原必绿。
 */
class InspectionConfigTest {

  @Test
  @DisplayName("默认回落必须是 5%（不是 0 不抽、也不是 100 全量）")
  void defaultsIsFivePercent() {
    InspectionConfig cfg = InspectionConfig.defaults();

    assertThat(cfg.samplingRatePercent()).isEqualTo(5);
    assertThat(cfg.fullyApplied()).isTrue();
    assertThat(cfg.rejected()).isEmpty();
  }

  @Test
  @DisplayName("显式配置 100 → 全量生效（V1.0 100% AI 质检口径）")
  void explicitHundredTakesEffect() {
    InspectionConfig cfg = InspectionConfig.from(Map.of("samplingRatePercent", 100));

    assertThat(cfg.samplingRatePercent()).isEqualTo(100);
    assertThat(cfg.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("null / 空 map → 回落 5%")
  void nullAndEmptyFallBack() {
    assertThat(InspectionConfig.from(null).samplingRatePercent()).isEqualTo(5);
    assertThat(InspectionConfig.from(Map.of()).samplingRatePercent()).isEqualTo(5);
  }

  @Test
  @DisplayName("缺键 / 非整数 / 越界 → 回落 5% 且登记拒绝原因（配了不生效要可见）")
  void illegalValuesFallBackWithReason() {
    InspectionConfig missing = InspectionConfig.from(Map.of("other", 1));
    assertThat(missing.samplingRatePercent()).isEqualTo(5);
    assertThat(missing.fullyApplied()).isFalse();
    assertThat(missing.rejected()).containsKey("samplingRatePercent");

    InspectionConfig nonInt = InspectionConfig.from(Map.of("samplingRatePercent", "abc"));
    assertThat(nonInt.samplingRatePercent()).isEqualTo(5);
    assertThat(nonInt.fullyApplied()).isFalse();

    InspectionConfig tooBig = InspectionConfig.from(Map.of("samplingRatePercent", 150));
    assertThat(tooBig.samplingRatePercent()).isEqualTo(5);
    assertThat(tooBig.fullyApplied()).isFalse();

    InspectionConfig negative = InspectionConfig.from(Map.of("samplingRatePercent", -1));
    assertThat(negative.samplingRatePercent()).isEqualTo(5);
    assertThat(negative.fullyApplied()).isFalse();
  }

  @Test
  @DisplayName("合法边界 0 与 100 均生效（0=不抽样是合法运维配置，不被当成非法回落）")
  void boundaryZeroAndHundredValid() {
    assertThat(InspectionConfig.from(Map.of("samplingRatePercent", 0)).samplingRatePercent())
        .isEqualTo(0);
    assertThat(InspectionConfig.from(Map.of("samplingRatePercent", 100)).samplingRatePercent())
        .isEqualTo(100);
  }
}
