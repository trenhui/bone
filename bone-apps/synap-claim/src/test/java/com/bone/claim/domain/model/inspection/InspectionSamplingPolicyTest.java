package com.bone.claim.domain.model.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 抽样策略测试：确定性（可复算）+ 比例边界 + 分布合理性 + 分层标识。
 *
 * <p><b>为什么断言分布而不是只断言「返回 boolean」</b>：抽样实现最典型的假绿是「永远返回 true（全抽）」或「永远返回
 * false（不抽）」——两者都能让调用方测试通过。这里用大样本命中率区间锁住真实比例，并按 ID 尾号连号段验证没有 「连号要么全抽要么全不抽」的低位规律（雪花/自增 ID 直接取模的经典坑）。
 */
class InspectionSamplingPolicyTest {

  @Test
  @DisplayName("同一案件重复判定结果一致（抽样可复算、可审计）")
  void deterministic() {
    for (long claimId = 1; claimId <= 200; claimId++) {
      boolean first = InspectionSamplingPolicy.shouldSample(claimId, 30);
      boolean second = InspectionSamplingPolicy.shouldSample(claimId, 30);
      assertThat(first).isEqualTo(second);
    }
  }

  @Test
  @DisplayName("比例边界：0 不抽、100 全抽、负数不抽、>100 全抽")
  void rateBounds() {
    for (long claimId = 1; claimId <= 50; claimId++) {
      assertThat(InspectionSamplingPolicy.shouldSample(claimId, 0)).isFalse();
      assertThat(InspectionSamplingPolicy.shouldSample(claimId, -5)).isFalse();
      assertThat(InspectionSamplingPolicy.shouldSample(claimId, 100)).isTrue();
      assertThat(InspectionSamplingPolicy.shouldSample(claimId, 150)).isTrue();
    }
  }

  @Test
  @DisplayName("5% 比例在大样本上命中率落在 [3.5%, 6.5%]（防「永远 true/false」假绿）")
  void hitRateMatchesConfiguredRate() {
    int total = 20_000;
    int hit = 0;
    for (long claimId = 1; claimId <= total; claimId++) {
      if (InspectionSamplingPolicy.shouldSample(claimId, 5)) {
        hit++;
      }
    }
    double rate = (double) hit / total;
    assertThat(rate).isBetween(0.035, 0.065);
  }

  @Test
  @DisplayName("连号案件不会被整段全抽或整段全不抽（低位规律检测）")
  void consecutiveIdsAreNotClustered() {
    // 取 100 个连号，期望出现「抽中/未抽中」交错；若实现是 claimId % 100 < 5，则整段只会有 5 个命中且相邻
    Set<Long> hits = new HashSet<>();
    for (long claimId = 1000; claimId < 1100; claimId++) {
      if (InspectionSamplingPolicy.shouldSample(claimId, 50)) {
        hits.add(claimId);
      }
    }
    assertThat(hits).isNotEmpty();
    assertThat(hits.size()).isBetween(40, 60);
    // 至少存在一处「抽中 → 未抽中」相邻切换，证明命中不是一整段前缀
    boolean hasSwitch = false;
    for (long claimId = 1000; claimId < 1099; claimId++) {
      if (InspectionSamplingPolicy.shouldSample(claimId, 50)
          != InspectionSamplingPolicy.shouldSample(claimId + 1, 50)) {
        hasSwitch = true;
        break;
      }
    }
    assertThat(hasSwitch).isTrue();
  }

  @Test
  @DisplayName("分层标识：金额段左闭右开 + 渠道大写 + 空渠道 NA")
  void stratum() {
    assertThat(InspectionSamplingPolicy.amountBand(new BigDecimal("499.99"))).isEqualTo("LT_500");
    assertThat(InspectionSamplingPolicy.amountBand(new BigDecimal("500"))).isEqualTo("500_1000");
    assertThat(InspectionSamplingPolicy.amountBand(new BigDecimal("999.99"))).isEqualTo("500_1000");
    assertThat(InspectionSamplingPolicy.amountBand(new BigDecimal("1000"))).isEqualTo("1000_5000");
    assertThat(InspectionSamplingPolicy.amountBand(new BigDecimal("5000"))).isEqualTo("GE_5000");
    assertThat(InspectionSamplingPolicy.amountBand(null)).isEqualTo("NA");

    assertThat(InspectionSamplingPolicy.sampleStratum(new BigDecimal("800"), "wechat"))
        .isEqualTo("500_1000#WECHAT");
    assertThat(InspectionSamplingPolicy.sampleStratum(new BigDecimal("800"), "  "))
        .isEqualTo("500_1000#NA");
    assertThat(InspectionSamplingPolicy.sampleStratum(null, null)).isEqualTo("NA#NA");
  }
}
