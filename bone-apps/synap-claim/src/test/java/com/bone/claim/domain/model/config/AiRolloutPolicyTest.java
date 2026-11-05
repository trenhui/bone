package com.bone.claim.domain.model.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 放量策略判据测试。
 *
 * <p>重点不在「解析对不对」，而在三条<b>会直接决定资金敞口</b>的判据：默认档必须是 SHADOW、坏配置必须回落而不是放行、 分桶必须确定性且不按 id 顺序集中。
 */
class AiRolloutPolicyTest {

  @Test
  @DisplayName("零配置 → SHADOW 且不放量任何案件（新租户默认全量自动放款在结构上不可能）")
  void defaultsAreShadow() {
    AiRolloutPolicy policy = AiRolloutPolicy.defaults();

    assertThat(policy.stage()).isEqualTo(AiRolloutStage.SHADOW);
    assertThat(policy.admitsCase(1L, 1L)).isFalse();
    assertThat(policy.admitsCase(1L, 999_999L)).isFalse();
  }

  @Test
  @DisplayName("阶段解析：容忍空白与大小写，但不猜拼写错误（回落 SHADOW 并登记原因）")
  void stageParsing() {
    Map<String, Object> ok = new HashMap<>();
    ok.put(AiRolloutPolicy.KEY_STAGE, " canary ");
    assertThat(AiRolloutPolicy.apply(ok).policy().stage()).isEqualTo(AiRolloutStage.CANARY);
    assertThat(AiRolloutPolicy.apply(ok).fullyApplied()).isTrue();

    Map<String, Object> bad = new HashMap<>();
    bad.put(AiRolloutPolicy.KEY_STAGE, "CANARYY");
    AiRolloutPolicy.Resolution resolution = AiRolloutPolicy.apply(bad);
    assertThat(resolution.policy().stage()).isEqualTo(AiRolloutStage.SHADOW);
    assertThat(resolution.rejected()).containsKey(AiRolloutPolicy.KEY_STAGE);
  }

  @Test
  @DisplayName("逐键回落：坏的一个键不影响其它键生效（否则配置者意图与实际相反）")
  void badKeyDoesNotVoidWholeConfig() {
    Map<String, Object> raw = new HashMap<>();
    raw.put(AiRolloutPolicy.KEY_STAGE, "CANARY");
    raw.put(AiRolloutPolicy.KEY_ERROR_RATE_TARGET, "2%");
    raw.put(AiRolloutPolicy.KEY_OBSERVATION_DAYS, 14);

    AiRolloutPolicy.Resolution resolution = AiRolloutPolicy.apply(raw);

    assertThat(resolution.policy().stage()).isEqualTo(AiRolloutStage.CANARY);
    assertThat(resolution.policy().observationDays()).isEqualTo(14);
    assertThat(resolution.policy().errorRateTarget()).isEqualByComparingTo("0.02");
    assertThat(resolution.rejected()).containsKey(AiRolloutPolicy.KEY_ERROR_RATE_TARGET);
  }

  @Test
  @DisplayName("错误率目标必须是 (0,1)：0 会让功能一上线就熔断，1 等于没有熔断")
  void errorRateTargetBounds() {
    for (String illegal : new String[] {"0", "1", "1.5", "-0.1"}) {
      Map<String, Object> raw = new HashMap<>();
      raw.put(AiRolloutPolicy.KEY_ERROR_RATE_TARGET, illegal);
      AiRolloutPolicy.Resolution resolution = AiRolloutPolicy.apply(raw);
      assertThat(resolution.policy().errorRateTarget()).isEqualByComparingTo("0.02");
      assertThat(resolution.rejected()).containsKey(AiRolloutPolicy.KEY_ERROR_RATE_TARGET);
    }
  }

  @Test
  @DisplayName("最小样本量必须 ≥1；观察窗必须 ≤31 天（超过即回落，不放宽扫描范围）")
  void sampleAndWindowBounds() {
    Map<String, Object> raw = new HashMap<>();
    raw.put(AiRolloutPolicy.KEY_MIN_INSPECTED_SAMPLES, 0);
    raw.put(AiRolloutPolicy.KEY_OBSERVATION_DAYS, 32);
    AiRolloutPolicy.Resolution resolution = AiRolloutPolicy.apply(raw);

    assertThat(resolution.policy().minInspectedSamples()).isEqualTo(30);
    assertThat(resolution.policy().observationDays()).isEqualTo(7);
    assertThat(resolution.rejected())
        .containsKeys(
            AiRolloutPolicy.KEY_MIN_INSPECTED_SAMPLES, AiRolloutPolicy.KEY_OBSERVATION_DAYS);
  }

  @Test
  @DisplayName("ASSISTED 永不放款、AUTOMATED 全量放行、CANARY 0% 全拦 100% 全放")
  void stageGateSemantics() {
    AiRolloutPolicy assisted =
        new AiRolloutPolicy(AiRolloutStage.ASSISTED, 100, new BigDecimal("0.02"), 30, 7);
    assertThat(assisted.admitsCase(1L, 1L)).isFalse();

    AiRolloutPolicy automated =
        new AiRolloutPolicy(AiRolloutStage.AUTOMATED, 0, new BigDecimal("0.02"), 30, 7);
    assertThat(automated.admitsCase(1L, 1L)).isTrue();

    AiRolloutPolicy zero =
        new AiRolloutPolicy(AiRolloutStage.CANARY, 0, new BigDecimal("0.02"), 30, 7);
    assertThat(zero.admitsCase(1L, 1L)).isFalse();

    AiRolloutPolicy full =
        new AiRolloutPolicy(AiRolloutStage.CANARY, 100, new BigDecimal("0.02"), 30, 7);
    assertThat(full.admitsCase(1L, 1L)).isTrue();
  }

  @Test
  @DisplayName("分桶确定性：同一案件重复判定必须同结果（否则重试会「时放时不放」）")
  void bucketingIsDeterministic() {
    AiRolloutPolicy policy =
        new AiRolloutPolicy(AiRolloutStage.CANARY, 30, new BigDecimal("0.02"), 30, 7);

    boolean first = policy.admitsCase(7L, 123_456_789L);
    for (int i = 0; i < 1000; i++) {
      assertThat(policy.admitsCase(7L, 123_456_789L)).isEqualTo(first);
    }
  }

  @Test
  @DisplayName("分桶不按 id 顺序：雪花样连续 id 在窗口两半的命中率都应接近目标比例")
  void bucketingDoesNotFollowIdOrder() {
    AiRolloutPolicy policy =
        new AiRolloutPolicy(AiRolloutStage.CANARY, 50, new BigDecimal("0.02"), 30, 7);

    int firstHalf = 0;
    int secondHalf = 0;
    for (long claimId = 1; claimId <= 1000; claimId++) {
      if (policy.admitsCase(7L, claimId)) {
        if (claimId <= 500) {
          firstHalf++;
        } else {
          secondHalf++;
        }
      }
    }
    // 若分桶退化成「id 小于某阈值即命中」，两半的差距会接近 500；这里要求各半都落在 35%–65%，
    // 即灰度样本不会挤在某个时间段里（挤在一起等于变相按时间放量）。
    assertThat(firstHalf).isBetween(175, 325);
    assertThat(secondHalf).isBetween(175, 325);
  }
}
