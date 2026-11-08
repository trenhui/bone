package com.bone.claim.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.DecisionMetricsPort.DecisionMetric;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 决策指标适配器语义：标签正交、空标签降级、耗时不为负。
 *
 * <p>这类埋点最容易"假绿"——只要有个计数器在涨就算过。此处刻意做负向对照：不同结论必须落在不同时序上，
 * 否则一个全局计数器就能蒙过所有断言，真出事时无法区分"人工转派飙升"还是"自动通过飙升"。
 */
class MicrometerDecisionMetricsAdapterTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MicrometerDecisionMetricsAdapter metrics =
      new MicrometerDecisionMetricsAdapter(registry);

  @Test
  @DisplayName("决策终态按 结论/档位/降级 三标签定位并计时")
  void recordsDecisionTagsAndTimer() {
    metrics.recordDecision(new DecisionMetric("MANUAL_REQUIRED", "MANUAL", true, 1200L));

    assertThat(
            registry
                .get("claim.decision.executions")
                .tags("result", "MANUAL_REQUIRED", "tier", "MANUAL", "fallback", "true")
                .counter()
                .count())
        .isEqualTo(1.0d);
    assertThat(
            registry
                .get("claim.decision.duration")
                .tags("result", "MANUAL_REQUIRED", "fallback", "true")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isGreaterThanOrEqualTo(1200.0d);
  }

  @Test
  @DisplayName("不同结论互不污染（防'一个计数器打天下'的假埋点）")
  void separatesSiblingResults() {
    metrics.recordDecision(new DecisionMetric("AUTO_APPROVE", "AUTO", false, 10L));
    metrics.recordDecision(new DecisionMetric("MANUAL_REQUIRED", "MANUAL", false, 10L));

    assertThat(countOf("AUTO_APPROVE")).isEqualTo(1.0d);
    assertThat(countOf("MANUAL_REQUIRED")).isEqualTo(1.0d);
    // 负向对照：未发生过的结论不得凭空出现计数
    assertThat(
            registry
                .find("claim.decision.executions")
                .tag("result", "NEED_MORE_EVIDENCE")
                .counter())
        .isNull();
  }

  @Test
  @DisplayName("空标签降级为 UNKNOWN（空字符串维度会污染时序聚合）")
  void blankTagsDegradeToUnknown() {
    metrics.recordDecision(new DecisionMetric(null, "  ", false, 0L));

    assertThat(
            registry
                .get("claim.decision.executions")
                .tags("result", "UNKNOWN", "tier", "UNKNOWN", "fallback", "false")
                .counter()
                .count())
        .isEqualTo(1.0d);
  }

  @Test
  @DisplayName("节点分布与耗时分别上报（定位是哪一段慢 / 哪一段在降级）")
  void recordsNodeDistributionAndDuration() {
    metrics.recordNode("AiAssess", "FALLBACK", 320L);

    assertThat(
            registry
                .get("claim.decision.node.executions")
                .tags("node", "AiAssess", "status", "FALLBACK")
                .counter()
                .count())
        .isEqualTo(1.0d);
    assertThat(
            registry
                .get("claim.decision.node.duration")
                .tag("node", "AiAssess")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isGreaterThanOrEqualTo(320.0d);
  }

  @Test
  @DisplayName("负耗时钳零（时钟回拨不得产生负时长，否则均值被拉低失真）")
  void clampsNegativeDurationToZero() {
    metrics.recordNode("RuleCheck", "SUCCEEDED", -500L);

    assertThat(
            registry
                .get("claim.decision.node.duration")
                .tag("node", "RuleCheck")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isEqualTo(0.0d);
  }

  private double countOf(String result) {
    return registry.get("claim.decision.executions").tag("result", result).counter().count();
  }
}
