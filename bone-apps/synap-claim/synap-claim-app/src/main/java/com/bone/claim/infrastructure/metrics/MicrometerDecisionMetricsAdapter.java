package com.bone.claim.infrastructure.metrics;

import com.bone.claim.application.port.out.DecisionMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 决策指标适配器（Micrometer）。
 *
 * <p>指标口径（标签基数均有界，勿引入案号/租户等高基数字段）：
 *
 * <ul>
 *   <li>{@code claim.decision.executions}：决策终态分布，标签 {@code result}（AUTO_APPROVE / MANUAL_REQUIRED /
 *       NEED_MORE_EVIDENCE / ...）、{@code tier}、{@code fallback}；
 *   <li>{@code claim.decision.duration}：决策端到端耗时；
 *   <li>{@code claim.decision.node.executions} / {@code claim.decision.node.duration}：单节点分布与耗时， 标签
 *       {@code node}、{@code status}。
 * </ul>
 *
 * <p>告警建议（生产就绪包 §5.3 burn-rate）：{@code fallback=true} 占比、{@code result=MANUAL_REQUIRED} 占比、 以及
 * {@code node=AiAssess,status=FALLBACK} 的环比突增，三者任一越阈即触发"冻结自动通过 / 转人工"人工确认。
 */
@Component
public class MicrometerDecisionMetricsAdapter implements DecisionMetricsPort {

  private static final String DECISION_TOTAL = "claim.decision.executions";
  private static final String DECISION_DURATION = "claim.decision.duration";
  private static final String NODE_TOTAL = "claim.decision.node.executions";
  private static final String NODE_DURATION = "claim.decision.node.duration";

  /** 空标签会污染时序（Prometheus 中表现为空字符串维度），统一降级为 UNKNOWN。 */
  private static final String UNKNOWN = "UNKNOWN";

  private final MeterRegistry registry;

  public MicrometerDecisionMetricsAdapter(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void recordDecision(DecisionMetric metric) {
    String result = safe(metric.result());
    String tier = safe(metric.tier());
    String fallback = Boolean.toString(metric.fallback());

    Counter.builder(DECISION_TOTAL)
        .description("理赔决策终态分布（按结论 / 档位 / 是否 AI 降级）")
        .tags("result", result, "tier", tier, "fallback", fallback)
        .register(registry)
        .increment();

    Timer.builder(DECISION_DURATION)
        .description("理赔决策图端到端耗时")
        .tags("result", result, "fallback", fallback)
        .register(registry)
        .record(nonNegative(metric.durationMillis()));
  }

  @Override
  public void recordNode(String nodeName, String status, long durationMillis) {
    String node = safe(nodeName);
    Counter.builder(NODE_TOTAL)
        .description("理赔决策单节点执行分布（按节点 / 状态）")
        .tags("node", node, "status", safe(status))
        .register(registry)
        .increment();

    Timer.builder(NODE_DURATION)
        .description("理赔决策单节点耗时")
        .tag("node", node)
        .register(registry)
        .record(nonNegative(durationMillis));
  }

  private static String safe(String v) {
    return v == null || v.isBlank() ? UNKNOWN : v;
  }

  private static Duration nonNegative(long millis) {
    return Duration.ofMillis(Math.max(0L, millis));
  }
}
