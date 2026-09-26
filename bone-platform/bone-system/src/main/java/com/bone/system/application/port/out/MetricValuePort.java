package com.bone.system.application.port.out;

import java.util.OptionalDouble;

/**
 * 指标值出站端口：按指标名解析当前实测值（告警评估的取数通道，E-10.2）。
 *
 * <p>为什么是端口而不是直接注入 {@code MeterRegistry}：评估用例只关心「指标名 → 数值」， 底层来自 Micrometer 还是外置 Prometheus 由
 * infrastructure 适配器决定；换数据源不动应用层。
 */
public interface MetricValuePort {

  /**
   * 解析指标当前值。
   *
   * @return 解析不到（指标不存在或口径不可计算）返回 {@code OptionalDouble.empty()}——「该指标此实现下不可评估」 是正常分支，评估器跳过即可，不是失败
   */
  OptionalDouble resolve(String metricName);
}
