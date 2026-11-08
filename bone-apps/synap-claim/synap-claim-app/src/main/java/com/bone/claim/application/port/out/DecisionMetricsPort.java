package com.bone.claim.application.port.out;

/**
 * 决策可观测性出站端口（指标埋点，C11 观测）。
 *
 * <p>生产就绪要求"可灰度、可告警、可回溯"：没有指标就无法发现 <b>自动通过率异常、AI 降级率飙升、决策耗时劣化</b>， 也无法为 SLO burn-rate
 * 告警与"冻结自动通过"降级提供触发信号（生产就绪包 §5.3）。本端口只声明"要记什么"， 具体注册表（Micrometer / OTLP / 自建）由 infrastructure
 * 适配器决定，应用层不依赖任何监控 SDK。
 */
public interface DecisionMetricsPort {

  /**
   * 记录一次决策的终态快照。
   *
   * <p>{@code fallback=true} 表示 AI 节点降级纯规则路径——这是需要持续观察的关键比率，而非普通成功。
   */
  void recordDecision(DecisionMetric metric);

  /** 记录单个决策节点的执行结果与耗时（定位"哪一段慢 / 哪一段在降级"）。 */
  void recordNode(String nodeName, String status, long durationMillis);

  /** 决策终态快照。 */
  record DecisionMetric(String result, String tier, boolean fallback, long durationMillis) {}

  /** 空实现：单测与未接入监控的环境使用（禁止用 {@code null} 表示"不采集"，避免调用点判空分支遗漏）。 */
  DecisionMetricsPort NOOP =
      new DecisionMetricsPort() {
        @Override
        public void recordDecision(DecisionMetric metric) {
          // 不采集
        }

        @Override
        public void recordNode(String nodeName, String status, long durationMillis) {
          // 不采集
        }
      };
}
