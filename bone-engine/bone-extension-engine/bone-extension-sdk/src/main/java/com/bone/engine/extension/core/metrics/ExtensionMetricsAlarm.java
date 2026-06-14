package com.bone.engine.extension.core.metrics;

import com.bone.engine.extension.support.config.ExtensionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 扩展点监控告警机制 当指标异常时自动告警 */
@Slf4j
@Component
public class ExtensionMetricsAlarm {

  private final ExtensionMetricsCollector metricsCollector;
  private final ExtensionAlarmService alarmService;
  private final ExtensionProperties properties;

  // 告警阈值配置
  private static final double FAILURE_RATE_THRESHOLD = 0.1; // 10% 失败率
  private static final long SLOW_INVOCATION_THRESHOLD = 100; // 100ms
  private static final int FAILURE_COUNT_THRESHOLD = 10; // 连续失败次数

  // 失败计数
  private final AtomicLong failureCount = new AtomicLong(0);
  // 上次告警时间
  private Instant lastAlarmTime = Instant.now();
  // 告警冷却时间
  private static final Duration ALARM_COOLDOWN = Duration.ofMinutes(5);

  @Autowired
  public ExtensionMetricsAlarm(
      ExtensionMetricsCollector metricsCollector,
      ExtensionAlarmService alarmService,
      ExtensionProperties properties) {
    this.metricsCollector = metricsCollector;
    this.alarmService = alarmService;
    this.properties = properties;
  }

  /** 定期检查监控指标并触发告警 */
  @Scheduled(fixedRate = 60000) // 每分钟检查一次
  public void checkMetricsAndAlarm() {
    try {
      // 检查监控是否启用
      if (!properties.getMonitor().isEnabled()) {
        return;
      }

      // 模拟检查失败率
      checkFailureRate();

      // 检查慢调用
      checkSlowInvocations();

      // 检查连续失败
      checkConsecutiveFailures();
    } catch (Exception e) {
      log.error("Error checking metrics for alarm", e);
    }
  }

  /** 检查失败率 */
  private void checkFailureRate() {
    // 实际实现中，从监控系统获取失败率
    // double failureRate = getFailureRateFromMetrics();
    // if (failureRate > FAILURE_RATE_THRESHOLD) {
    //     Map<String, Object> context = new HashMap<>();
    //     context.put("failureRate", failureRate);
    //     context.put("threshold", FAILURE_RATE_THRESHOLD);
    //     triggerAlarm(ExtensionAlarmService.AlarmLevel.WARNING, "High failure rate detected",
    // context);
    // }
    log.debug("Checking failure rate...");
  }

  /** 检查慢调用 */
  private void checkSlowInvocations() {
    // 实际实现中，从监控系统获取慢调用率
    log.debug("Checking slow invocations...");
  }

  /** 检查连续失败 */
  private void checkConsecutiveFailures() {
    // 实际实现中，从监控系统获取连续失败次数
    log.debug("Checking consecutive failures...");
  }

  /**
   * 触发告警
   *
   * @param level 告警级别
   * @param message 告警消息
   * @param context 上下文信息
   */
  private void triggerAlarm(
      ExtensionAlarmService.AlarmLevel level, String message, Map<String, Object> context) {
    // 检查告警冷却时间
    if (Instant.now().isBefore(lastAlarmTime.plus(ALARM_COOLDOWN))) {
      log.debug("Alarm cooldown period active, skipping alarm: {}", message);
      return;
    }

    // 使用告警服务发送告警
    alarmService.sendAlarm(level, message, context);

    // 更新上次告警时间
    lastAlarmTime = Instant.now();
  }

  /** 记录失败 */
  public void recordFailure() {
    long count = failureCount.incrementAndGet();
    if (count >= FAILURE_COUNT_THRESHOLD) {
      Map<String, Object> context = new HashMap<>();
      context.put("failureCount", count);
      context.put("threshold", FAILURE_COUNT_THRESHOLD);
      triggerAlarm(
          ExtensionAlarmService.AlarmLevel.WARNING, "Consecutive failures detected", context);
      failureCount.set(0); // 重置计数
    }
  }

  /** 重置失败计数 */
  public void resetFailureCount() {
    failureCount.set(0);
  }
}
