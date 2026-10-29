package com.bone.workflow.starter.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.workflow.starter.metrics.WorkflowMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 死信积压巡检（P0-2 完善）行为测试。
 *
 * <p><b>判别性设计（防假绿）</b>：断言 {@code dead_letter_depth} Gauge 被刷成终态 FAILED 存量值——若 {@code run()} 没调用
 * {@code countTerminallyFailed} 或没把值写进 Gauge，断言即失败。另含「无死信归零」与「metrics 为 null 不 NPE」两条守卫。
 */
class OutboxDeadLetterSweepTest {

  @Test
  @DisplayName("P0-2 完善：死信巡检把终态 FAILED 存量刷成 dead_letter_depth Gauge")
  void run_refreshesDeadLetterDepthGauge() {
    WorkflowStatusOutboxRepository outbox = mock(WorkflowStatusOutboxRepository.class);
    when(outbox.countTerminallyFailed()).thenReturn(3L);

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WorkflowMetrics metrics = new WorkflowMetrics(registry);
    OutboxDeadLetterSweep sweep = new OutboxDeadLetterSweep(outbox, metrics);

    sweep.run();

    assertEquals(
        3.0,
        registry.get("bone.workflow.outbox.dead_letter_depth").gauge().value(),
        0.001,
        "死信存量必须刷新到 Gauge，否则积压无人察觉");
  }

  @Test
  @DisplayName("P0-2 完善：无死信时 Gauge 归零（不只是「不报错」）")
  void run_zeroWhenNoDeadLetter() {
    WorkflowStatusOutboxRepository outbox = mock(WorkflowStatusOutboxRepository.class);
    when(outbox.countTerminallyFailed()).thenReturn(0L);

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WorkflowMetrics metrics = new WorkflowMetrics(registry);
    OutboxDeadLetterSweep sweep = new OutboxDeadLetterSweep(outbox, metrics);

    sweep.run();

    assertEquals(
        0.0,
        registry.get("bone.workflow.outbox.dead_letter_depth").gauge().value(),
        0.001,
        "无死信时 Gauge 必须为 0");
  }

  @Test
  @DisplayName("P0-2 完善：metrics 为 null 时 run() 不抛 NPE（降级到 NOOP）")
  void run_nullMetrics_noNpe() {
    WorkflowStatusOutboxRepository outbox = mock(WorkflowStatusOutboxRepository.class);
    when(outbox.countTerminallyFailed()).thenReturn(5L);

    OutboxDeadLetterSweep sweep = new OutboxDeadLetterSweep(outbox, null);
    // 仅断言不抛异常
    sweep.run();
  }
}
