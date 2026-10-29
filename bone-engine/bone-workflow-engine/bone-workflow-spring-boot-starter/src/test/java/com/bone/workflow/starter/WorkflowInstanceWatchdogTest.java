package com.bone.workflow.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.starter.metrics.WorkflowMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 卡死实例看门狗（§11.4 决策 #2）行为测试。
 *
 * <p><b>判别性设计（防假绿）</b>：断言 {@code instance.stuck} Gauge 被刷成卡死实例数——若 {@code run()} 没调用 {@code
 * findStuckInstances} 或没把数量写进 Gauge，断言即失败。另含「无卡死归零」与「metrics 为 null 不 NPE」两条守卫。 真实的 SQL 判据（RUNNING
 * + 停留超阈值 + 无 PENDING）由 {@code WorkflowInstanceWatchdogH2Test} 在 H2 上覆盖。
 */
class WorkflowInstanceWatchdogTest {

  @Test
  @DisplayName("§11.4#2：看门狗把卡死实例数刷成 instance.stuck Gauge")
  void run_refreshesStuckGauge() {
    WorkflowInstanceRepository repo = mock(WorkflowInstanceRepository.class);
    when(repo.findStuckInstances(any())).thenReturn(List.of("a", "b"));

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WorkflowMetrics metrics = new WorkflowMetrics(registry);
    WorkflowInstanceWatchdog watchdog =
        new WorkflowInstanceWatchdog(repo, metrics, Duration.ofMinutes(60));

    watchdog.run();

    assertEquals(
        2.0,
        registry.get("bone.workflow.instance.stuck").gauge().value(),
        0.001,
        "卡死实例数必须刷新到 Gauge，否则死实例无人察觉");
  }

  @Test
  @DisplayName("§11.4#2：无卡死时 Gauge 归零")
  void run_zeroWhenNoneStuck() {
    WorkflowInstanceRepository repo = mock(WorkflowInstanceRepository.class);
    when(repo.findStuckInstances(any())).thenReturn(List.of());

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WorkflowMetrics metrics = new WorkflowMetrics(registry);
    WorkflowInstanceWatchdog watchdog =
        new WorkflowInstanceWatchdog(repo, metrics, Duration.ofMinutes(60));

    watchdog.run();

    assertEquals(
        0.0,
        registry.get("bone.workflow.instance.stuck").gauge().value(),
        0.001,
        "无卡死时 Gauge 必须为 0");
  }

  @Test
  @DisplayName("§11.4#2：metrics 为 null 时 run() 不抛 NPE（降级到 NOOP）")
  void run_nullMetrics_noNpe() {
    WorkflowInstanceRepository repo = mock(WorkflowInstanceRepository.class);
    when(repo.findStuckInstances(any())).thenReturn(List.of("x"));

    WorkflowInstanceWatchdog watchdog =
        new WorkflowInstanceWatchdog(repo, null, Duration.ofMinutes(60));
    watchdog.run();
  }
}
